import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { OrganizationMapper } from './organization.mapper';
import { OrganizationService } from './organization.service';

describe('OrganizationService', () => {
  const root = 'http://localhost:8080/api';
  const organizationsUrl = `${root}/platform/organizations`;
  let service: OrganizationService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { PLATFORM_ADMIN_SERVICE_ROOT: root } } },
        OrganizationMapper,
        OrganizationService,
      ],
    });
    service = TestBed.inject(OrganizationService);
    controller = TestBed.inject(HttpTestingController);
  });

  it.each([
    ['suspend', 'suspend', 'SUSPENDED'],
    ['activate', 'activate', 'ACTIVE'],
  ] as const)('posts %s and maps the returned organization', async (method, verb, status) => {
    const pending = firstValueFrom(service[method]('acme'));
    const request = controller.expectOne(`${organizationsUrl}/acme/${verb}`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({ key: 'acme', status });
    expect((await pending).status).toBe(status);
  });

  it('posts the administrator invitation unchanged', async () => {
    const user = { username: 'ada', email: 'ada@example.test', firstName: 'Ada' };
    const pending = firstValueFrom(service.assignAdmin('acme', user));
    const request = controller.expectOne(`${organizationsUrl}/acme/admin-user`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(user);
    request.flush({ id: '1', username: 'ada', realm: 'acme' });
    expect((await pending).realm).toBe('acme');
  });
});
