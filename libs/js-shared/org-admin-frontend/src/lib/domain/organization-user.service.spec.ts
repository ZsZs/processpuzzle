import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { OrganizationUser } from './organization-user';
import { OrganizationRoleMapper, OrganizationUserMapper } from './organization-user.mapper';
import { ORG_ADMIN_ORG_KEY, OrganizationRoleService, OrganizationUserService } from './organization-user.service';

const USER_DTO = { id: 'kc-1', username: 'ada', email: 'ada@my-org.example', firstName: 'Ada', lastName: 'Lovelace', enabled: true, emailVerified: true, roles: ['org-member'] };

describe('OrganizationUserService', () => {
  const serviceRoot = 'http://localhost:8080/api';
  const usersUrl = `${serviceRoot}/organizations/my-org/admin/users`;

  function configure(baseConfiguration: object, orgKey = 'my-org') {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: baseConfiguration } },
        { provide: ORG_ADMIN_ORG_KEY, useValue: orgKey },
        OrganizationUserMapper,
        OrganizationUserService,
      ],
    });
    return { service: TestBed.inject(OrganizationUserService), controller: TestBed.inject(HttpTestingController) };
  }

  let service: OrganizationUserService;
  let controller: HttpTestingController;

  beforeEach(() => {
    ({ service, controller } = configure({ ORG_ADMIN_SERVICE_ROOT: serviceRoot }));
  });

  // The tenant is in the resource URL, not in a header and not in a query parameter, because that is
  // where org-admin-api.yaml puts it - and under `/admin` rather than directly under the organization
  // because base-workflow already owns `/organizations/{orgKey}/roles` for workflow role definitions.
  it('addresses the users of the tenant the token was resolved for', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(usersUrl);
    expect(request.request.method).toBe('GET');
    request.flush([USER_DTO]);

    const users = (await pending) as PersistedEntity<OrganizationUser>[];
    expect(users[0].username).toBe('ada');
  });

  it('reads a page of users as readily as a bare array', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(usersUrl).flush({ content: [USER_DTO], number: 0, size: 20, totalElements: 1, totalPages: 1 });

    const page = (await pending) as { totalElements: number; content: PersistedEntity<OrganizationUser>[] };
    expect(page.totalElements).toBe(1);
    expect(page.content[0].id).toBe('kc-1');
  });

  /**
   * The one deviation from the generic service. This resource has no database behind it, so the
   * backend hands the parameter to the identity provider's own free-text user search rather than
   * parsing RSQL - and the parameter it reads is named `search`. Left at the inherited `where` the
   * filter would be dropped silently and every search would answer with the unfiltered list.
   */
  it('sends a free-text filter as `search`, not as the inherited `where`', () => {
    service.findByQuery({ query: 'ada' }).subscribe();

    // `buildFullUrl` writes the parameters into the URL string rather than into `HttpParams`, so the
    // assertion reads them off the URL - `request.params` is empty for every request this base class makes.
    const request = controller.expectOne((candidate) => candidate.url.startsWith(usersUrl));
    expect(request.request.url).toBe(`${usersUrl}?search=ada`);
    request.flush([USER_DTO]);
  });

  it('keeps paging and sorting on the shared parameter names', () => {
    service.findByQuery({ page: 2, pageSize: 20, orderBys: [{ property: 'username', direction: 'asc' }] }).subscribe();

    const request = controller.expectOne((candidate) => candidate.url.startsWith(usersUrl));
    expect(decodeURIComponent(request.request.url)).toBe(`${usersUrl}?page=2&size=20&order=username,asc`);
    request.flush([USER_DTO]);
  });

  it('addresses one user by the identity provider id', () => {
    service.delete('kc-1').subscribe();

    const request = controller.expectOne(`${usersUrl}/kc-1`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('sends only the updatable profile fields on update', () => {
    const entity = new OrganizationUserMapper().fromDto(USER_DTO) as PersistedEntity<OrganizationUser>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${usersUrl}/kc-1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ email: 'ada@my-org.example', firstName: 'Ada', lastName: 'Lovelace', enabled: true });
    request.flush(USER_DTO);
  });

  /**
   * The invitation is not `add`. `add` sends what `toDto` produces, and `toDto` models the update
   * payload - it drops `username`, which `OrganizationUserInvite` requires, so an invitation routed
   * through it would come back 400 from a screen that looks like it worked.
   */
  it('posts the invitation payload, username and roles included', () => {
    service.invite({ username: 'ada', email: 'ada@my-org.example', firstName: 'Ada', roles: ['accountant'] }).subscribe();

    const request = controller.expectOne(usersUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ username: 'ada', email: 'ada@my-org.example', firstName: 'Ada', roles: ['accountant'] });
    request.flush(USER_DTO);
  });

  it('answers an invitation with the user the identity provider stored', async () => {
    const pending = firstValueFrom(service.invite({ username: 'ada', email: 'ada@my-org.example', roles: [] }));

    controller.expectOne(usersUrl).flush({ ...USER_DTO, roles: ['org-member'] });

    const invited = await pending;
    // The provider decides what it stored - including the `org-member` it adds regardless of the
    // chosen roles - so the response is what the caller reports, not the payload it sent.
    expect(invited.id).toBe('kc-1');
    expect(invited.roles).toEqual(['org-member']);
  });

  // ORG_ADMIN_SERVICE_ROOT is optional by contract; `serviceRootOf` falls back to APP_SERVICE_ROOT,
  // which is the only root this workspace's deployments actually configure today.
  it('falls back to APP_SERVICE_ROOT when no org-admin root is configured', () => {
    const { service: fallbackService, controller: fallbackController } = configure({ APP_SERVICE_ROOT: serviceRoot });

    fallbackService.delete('kc-1').subscribe();

    fallbackController.expectOne(`${usersUrl}/kc-1`).flush(null);
  });

  // The token is read inside the constructor's `super(...)` call, so the URL is fixed once and cannot
  // be re-pointed at another tenant afterwards. That is why the host mounts the branch at a literal
  // `{orgKey}/admin` path: a URL naming a different tenant has to force a fresh bootstrap.
  it('bakes the tenant into the resource URL at construction', () => {
    const { service: otherService, controller: otherController } = configure({ ORG_ADMIN_SERVICE_ROOT: serviceRoot }, 'other-org');

    otherService.delete('kc-1').subscribe();

    otherController.expectOne(`${serviceRoot}/organizations/other-org/admin/users/kc-1`).flush(null);
  });
});

describe('OrganizationRoleService', () => {
  const serviceRoot = 'http://localhost:8080/api';
  const adminUrl = `${serviceRoot}/organizations/my-org/admin`;
  let service: OrganizationRoleService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ORG_ADMIN_SERVICE_ROOT: serviceRoot } } },
        { provide: ORG_ADMIN_ORG_KEY, useValue: 'my-org' },
        OrganizationRoleMapper,
        OrganizationRoleService,
      ],
    });
    service = TestBed.inject(OrganizationRoleService);
    controller = TestBed.inject(HttpTestingController);
  });

  // Read live rather than from a fixed enum: beyond `org-admin` and `org-member` the roles are the
  // tenant's own, and they are what NavNode.roles and workflow role definitions match against.
  it('reads the realm roles the tenant declares', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${adminUrl}/roles`);
    expect(request.request.method).toBe('GET');
    request.flush([
      { name: 'org-admin', description: 'Administers.', platformManaged: true },
      { name: 'accountant', platformManaged: false },
    ]);

    const roles = await pending;
    expect(roles.map((role) => role.name)).toEqual(['org-admin', 'accountant']);
    expect(roles[0].platformManaged).toBe(true);
    // A role's id mirrors its name - the realm has no separate key, and the checkbox set tracks by it.
    expect(roles[1].id).toBe('accountant');
  });

  it('reads the roles one user holds from a sub-resource of that user', () => {
    service.findByUser('kc-1').subscribe();

    controller.expectOne(`${adminUrl}/users/kc-1/roles`).flush([{ name: 'org-member', platformManaged: true }]);
  });

  /**
   * A full replacement, not add and remove. Two people editing the assignment screen at once must not
   * silently merge into a union neither chose, so the whole set goes in one PUT and the backend
   * computes the grants and revocations from the difference.
   */
  it('replaces the whole role set in one PUT', async () => {
    const pending = firstValueFrom(service.replace('kc-1', ['org-admin', 'accountant']));

    const request = controller.expectOne(`${adminUrl}/users/kc-1/roles`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ roles: ['org-admin', 'accountant'] });
    request.flush([{ name: 'org-admin' }, { name: 'accountant' }]);

    expect((await pending).map((role) => role.name)).toEqual(['org-admin', 'accountant']);
  });

  it('copies the roles into the payload rather than sending the array it was handed', () => {
    const roles = ['org-admin'];

    service.replace('kc-1', roles).subscribe();
    roles.push('mutated-after-the-call');

    const request = controller.expectOne(`${adminUrl}/users/kc-1/roles`);
    expect(request.request.body).toEqual({ roles: ['org-admin'] });
    request.flush([]);
  });
});
