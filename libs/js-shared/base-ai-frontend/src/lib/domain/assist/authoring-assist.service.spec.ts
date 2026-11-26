import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EXPECTED_ERROR_STATUSES, RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthoringAssistService } from './authoring-assist.service';

const ROOT = 'https://backend/organizations/acme';

describe('AuthoringAssistService', () => {
  let http: HttpTestingController;

  function create(configuration: object | null = { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT } }): AuthoringAssistService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: configuration }],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthoringAssistService);
  }

  afterEach(() => http.verify());

  it('asks once, at construction, which tasks the user can use', () => {
    const service = create();

    const request = http.expectOne(`${ROOT}/ai/assist`);
    expect(request.request.method).toBe('GET');
    request.flush({ tasks: ['rule.expression'] });

    expect(service.isAvailable('rule.expression')()).toBe(true);
    expect(service.isAvailable('calculation.source')()).toBe(false);
  });

  it('is unavailable until the answer arrives, and the signal follows it', () => {
    const service = create();
    const available = service.isAvailable('rule.expression');
    expect(available()).toBe(false);

    http.expectOne(`${ROOT}/ai/assist`).flush({ tasks: ['rule.expression'] });

    expect(available()).toBe(true);
  });

  it.each([401, 403, 404])('treats a %i of the probe as "not available" that the error interceptor must not report', (status) => {
    const service = create();

    const request = http.expectOne(`${ROOT}/ai/assist`);
    expect(request.request.context.get(EXPECTED_ERROR_STATUSES)).toContain(status);
    request.flush({ message: 'no' }, { status, statusText: 'no' });

    expect(service.isAvailable('rule.expression')()).toBe(false);
  });

  it('treats any other failure of the probe as no tasks too', () => {
    const service = create();

    http.expectOne(`${ROOT}/ai/assist`).flush(null, { status: 500, statusText: 'boom' });

    expect(service.isAvailable('rule.expression')()).toBe(false);
  });

  it('treats an answer without tasks as none', () => {
    const service = create();

    http.expectOne(`${ROOT}/ai/assist`).flush({});

    expect(service.isAvailable('rule.expression')()).toBe(false);
  });

  it('makes no request when no backend is configured', () => {
    const service = create(null);

    expect(service.isAvailable('rule.expression')()).toBe(false);
    http.expectNone(`${ROOT}/ai/assist`);
  });

  it('posts the request to the task and answers its result', async () => {
    const service = create();
    http.expectOne(`${ROOT}/ai/assist`).flush({ tasks: ['rule.expression'] });

    const result = service.run('rule.expression', { text: 'The total must be positive', parameters: { context: 'Order' }, current: '' });
    const request = http.expectOne(`${ROOT}/ai/assist/rule.expression`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ text: 'The total must be positive', parameters: { context: 'Order' }, current: '' });
    request.flush({ candidate: 'entity.total > 0', explanation: 'x', assumptions: [], issues: [], rounds: 1, model: 'pp' });

    await expect(result).resolves.toMatchObject({ candidate: 'entity.total > 0', rounds: 1 });
  });

  it('rejects when the request fails, for the caller to report', async () => {
    const service = create();
    http.expectOne(`${ROOT}/ai/assist`).flush({ tasks: [] });

    const result = service.run('rule.expression', { text: 'x' });
    http.expectOne(`${ROOT}/ai/assist/rule.expression`).flush({ message: 'down' }, { status: 502, statusText: 'Bad Gateway' });

    await expect(result).rejects.toMatchObject({ status: 502 });
  });
});
