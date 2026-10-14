import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { RegistrationError, RegistrationService } from './registration.service';

describe('RegistrationService', () => {
  let http: HttpTestingController;
  let service: RegistrationService;

  function configure(baseConfiguration: object) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: baseConfiguration } }],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(RegistrationService);
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('posts the registration to the server root', async () => {
    configure({ REGISTRATION_SERVICE_ROOT: 'http://localhost:8080/' });

    const result = service.register({ email: 'ada@example.com', password: 'pw' });
    const request = http.expectOne('http://localhost:8080/registration');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'ada@example.com', password: 'pw' });
    request.flush(null, { status: 201, statusText: 'Created' });

    await expect(result).resolves.toBeUndefined();
  });

  it('falls back to OBJECT_STORE_SERVICE_ROOT', async () => {
    configure({ OBJECT_STORE_SERVICE_ROOT: '/api' });

    const result = service.register({ email: 'ada@example.com', password: 'pw' });
    http.expectOne('/api/registration').flush(null, { status: 201, statusText: 'Created' });

    await expect(result).resolves.toBeUndefined();
  });

  it('reports the backend errorId as the error code', async () => {
    configure({ OBJECT_STORE_SERVICE_ROOT: '/api' });

    const result = service.register({ email: 'ada@example.com', password: 'pw' });
    http.expectOne('/api/registration').flush({ errorId: 'user.already-exists', errorText: 'taken' }, { status: 409, statusText: 'Conflict' });

    await expect(result).rejects.toMatchObject({ code: 'user.already-exists', status: 409 });
    await result.catch((error) => expect(error).toBeInstanceOf(RegistrationError));
  });

  it('reports an unreachable backend', async () => {
    configure({ OBJECT_STORE_SERVICE_ROOT: '/api' });

    const result = service.register({ email: 'ada@example.com', password: 'pw' });
    http.expectOne('/api/registration').error(new ProgressEvent('error'));

    await expect(result).rejects.toMatchObject({ code: 'registration.unreachable' });
  });
});
