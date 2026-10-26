import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom, Observable } from 'rxjs';
import { BaseEntityAttrDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ORGANIZATION_ROLE_ENTITY_NAME, createOrganizationRoleDescriptor, ROLE_NAME_PATTERN } from './organization-role.descriptors';
import { OrganizationRole } from './organization-user';
import { OrganizationRoleMapper } from './organization-user.mapper';
import { ORG_ADMIN_ORG_KEY, OrganizationRoleEntityService } from './organization-user.service';

const ROLES = [
  { name: 'org-admin', description: 'Administrators', platformManaged: true, origin: 'platform' },
  { name: 'reviewer', description: 'Reviews', platformManaged: false, origin: 'workflow' },
  { name: 'accounting', description: 'Books', platformManaged: false, origin: 'admin' },
];

/** An administrator-defined role, as the store holds it once persisted: the entity keyed by its name. */
class PersistedAccounting extends OrganizationRole {
  constructor() {
    super('accounting', 'Books', false, 'admin');
  }
}

describe('OrganizationRole, the mapper and the entity service', () => {
  const serviceRoot = 'http://localhost:8080/api';
  const rolesUrl = `${serviceRoot}/organizations/my-org/admin/roles`;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ORG_ADMIN_SERVICE_ROOT: serviceRoot } } },
        { provide: ORG_ADMIN_ORG_KEY, useValue: 'my-org' },
        OrganizationRoleMapper,
        OrganizationRoleEntityService,
      ],
    });
  });

  it('reads who manages a role, and the role is changeable only when the administrator defined it', () => {
    const mapper = TestBed.inject(OrganizationRoleMapper);

    expect(mapper.fromDto(ROLES[2]).origin).toBe('admin');
    expect(mapper.fromDto(ROLES[2]).administratorManaged).toBe(true);
    expect(mapper.fromDto(ROLES[1]).administratorManaged).toBe(false);
    expect(new OrganizationRole('org-admin', undefined, true).origin).toBeUndefined();
  });

  it('sends the name and the description, and nothing else of the role', () => {
    const mapper = TestBed.inject(OrganizationRoleMapper);
    const role = mapper.fromDto(ROLES[2]);

    expect(mapper.toDto(role)).toEqual({ name: 'accounting', description: 'Books' });
    expect(mapper.toDto(new OrganizationRole('audit'))).toEqual({ name: 'audit', description: '' });
  });

  it('lists the roles the screen shows, as the backend answers them', async () => {
    const service = TestBed.inject(OrganizationRoleEntityService);
    const controller = TestBed.inject(HttpTestingController);

    const listed = firstValueFrom(service.findAll() as Observable<OrganizationRole[]>);
    controller.expectOne(rolesUrl).flush(ROLES);

    expect((await listed).map((role) => role.name)).toEqual(['org-admin', 'reviewer', 'accounting']);
  });

  it('reads one role out of the list, since the contract has no read of a single role', async () => {
    const service = TestBed.inject(OrganizationRoleEntityService);
    const controller = TestBed.inject(HttpTestingController);

    const found = firstValueFrom(service.findById('reviewer') as Observable<OrganizationRole>);
    controller.expectOne(rolesUrl).flush(ROLES);

    expect((await found).name).toBe('reviewer');
  });

  it('answers nothing for a role the list does not hold', async () => {
    const service = TestBed.inject(OrganizationRoleEntityService);
    const controller = TestBed.inject(HttpTestingController);

    const found = firstValueFrom(service.findById('ghost'));
    controller.expectOne(rolesUrl).flush(ROLES);

    expect(await found).toBeUndefined();
  });

  it('refuses to delete the whole collection, since roles go one at a time, by name', async () => {
    const service = TestBed.inject(OrganizationRoleEntityService);
    const controller = TestBed.inject(HttpTestingController);

    await expect(firstValueFrom(service.deleteAll())).rejects.toThrow('one at a time');
    controller.verify();
  });

  it('updates a role by its name, with the description', () => {
    const service = TestBed.inject(OrganizationRoleEntityService);
    const controller = TestBed.inject(HttpTestingController);

    service.update(new PersistedAccounting()).subscribe();
    const request = controller.expectOne(`${rolesUrl}/accounting`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'accounting', description: 'Books' });
    request.flush({ name: 'accounting', description: 'Books', origin: 'admin' });
  });
});

describe('the role descriptor', () => {
  const descriptor = createOrganizationRoleDescriptor();
  /** The attribute of that name, looked up through the flexbox layout the descriptor nests its attributes in. */
  const attr = (name: string): BaseEntityAttrDescriptor | undefined => attributesOf(descriptor.attrDescriptors).find((a) => a.attrName === name);

  it('is the organization role entity, titled by its name', () => {
    expect(descriptor.entityName).toBe(ORGANIZATION_ROLE_ENTITY_NAME);
    expect(descriptor.titleKey).toBe('name');
  });

  it('fixes the name once the role exists, and checks its shape while it is chosen', () => {
    const name = attr('name');

    expect(name?.createOnly).toBe(true);
    expect(name?.pattern).toBe(ROLE_NAME_PATTERN);
    expect(name?.required).toBe(true);
  });

  it('shows the origin and never lets it be set', () => {
    const origin = attr('origin');

    expect(origin?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(origin?.disabled).toBe(true);
  });

  it('lets an administrator change or delete only the roles the administrator defined', () => {
    expect(descriptor.isEditable?.(new OrganizationRole('accounting', 'Books', false, 'admin'))).toBe(true);
    expect(descriptor.isDeletable?.(new OrganizationRole('accounting', 'Books', false, 'admin'))).toBe(true);
    expect(descriptor.isEditable?.(new OrganizationRole('org-admin', undefined, true, 'platform'))).toBe(false);
    expect(descriptor.isDeletable?.(new OrganizationRole('reviewer', 'Reviews', false, 'workflow'))).toBe(false);
  });
});

function attributesOf(nodes: unknown[]): BaseEntityAttrDescriptor[] {
  return nodes.flatMap((node) =>
    node instanceof BaseEntityAttrDescriptor ? [node] : attributesOf((node as { attrDescriptors?: unknown[] }).attrDescriptors ?? []),
  );
}
