import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { SelfServiceRolesError, SelfServiceRolesService } from './self-service-roles.service';

describe('SelfServiceRolesService', () => {
  let http: HttpTestingController;
  let service: SelfServiceRolesService;

  function configure(baseConfiguration: object) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: baseConfiguration } }],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(SelfServiceRolesService);
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('is unavailable without its root, even when a backend root exists', () => {
    configure({ BACKEND_SERVICE_ROOT: '/api/organizations/acme' });

    expect(service.available).toBe(false);
  });

  it('reads the roles from the organization-scoped root', async () => {
    configure({ SELF_SERVICE_ROLES_SERVICE_ROOT: '/api/organizations/acme/' });
    expect(service.available).toBe(true);

    const result = service.load();
    const request = http.expectOne('/api/organizations/acme/me/roles');
    expect(request.request.method).toBe('GET');
    request.flush({ assigned: ['org-member'], selectable: [{ name: 'documentation-editor' }] });

    await expect(result).resolves.toEqual({ assigned: ['org-member'], selectable: [{ name: 'documentation-editor' }] });
  });

  it('puts the chosen role names', async () => {
    configure({ SELF_SERVICE_ROLES_SERVICE_ROOT: '/api/organizations/acme' });

    const result = service.replace(['documentation-editor']);
    const request = http.expectOne('/api/organizations/acme/me/roles');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ roles: ['documentation-editor'] });
    request.flush({ assigned: ['documentation-editor'], selectable: [] });

    await expect(result).resolves.toMatchObject({ assigned: ['documentation-editor'] });
  });

  it('reports the backend errorId as the error code', async () => {
    configure({ SELF_SERVICE_ROLES_SERVICE_ROOT: '/api/organizations/acme' });

    const result = service.replace(['org-admin']);
    http.expectOne('/api/organizations/acme/me/roles').flush({ errorId: 'role.unknown', errorText: 'no' }, { status: 400, statusText: 'Bad Request' });

    await expect(result).rejects.toMatchObject({ code: 'role.unknown', status: 400 });
    await result.catch((error) => expect(error).toBeInstanceOf(SelfServiceRolesError));
  });

  it('reports an unreachable backend', async () => {
    configure({ SELF_SERVICE_ROLES_SERVICE_ROOT: '/api/organizations/acme' });

    const result = service.load();
    http.expectOne('/api/organizations/acme/me/roles').error(new ProgressEvent('error'));

    await expect(result).rejects.toMatchObject({ code: 'self-service-roles.unreachable' });
  });
});
