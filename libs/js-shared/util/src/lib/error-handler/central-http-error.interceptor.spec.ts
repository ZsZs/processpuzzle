import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ErrorHandler } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { centralHttpErrorInterceptor, EXPECTED_ERROR_STATUSES } from './central-http-error.interceptor';

describe('centralHttpErrorInterceptor', () => {
  let httpClient: HttpClient;
  let httpTestingController: HttpTestingController;
  let errorHandler: Pick<ErrorHandler, 'handleError'>;
  let handledErrors: unknown[];

  beforeEach(() => {
    handledErrors = [];
    errorHandler = {
      handleError(error: unknown): void {
        handledErrors.push(error);
      },
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([centralHttpErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: ErrorHandler, useValue: errorHandler },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('forwards HTTP errors to the configured ErrorHandler and rethrows them', () => {
    const errorSpy = vi.fn();

    httpClient.get('/api/workflows').subscribe({ error: errorSpy });

    const request = httpTestingController.expectOne('/api/workflows');
    request.flush({ message: 'Service unavailable' }, { status: 503, statusText: 'Service Unavailable' });

    expect(handledErrors).toHaveLength(1);
    expect(errorSpy).toHaveBeenCalledWith(handledErrors[0]);
  });

  it('does not report a status the request declared as expected, but still rethrows it', () => {
    const errorSpy = vi.fn();
    const context = new HttpContext().set(EXPECTED_ERROR_STATUSES, [404]);

    httpClient.get('/api/app/layout', { context }).subscribe({ error: errorSpy });

    httpTestingController.expectOne('/api/app/layout').flush(null, { status: 404, statusText: 'Not Found' });

    expect(handledErrors).toHaveLength(0);
    expect(errorSpy).toHaveBeenCalledTimes(1);
  });

  it('still reports an unexpected status of a request that declared others', () => {
    const context = new HttpContext().set(EXPECTED_ERROR_STATUSES, [404]);

    httpClient.get('/api/app/layout', { context }).subscribe({ error: vi.fn() });

    httpTestingController.expectOne('/api/app/layout').flush(null, { status: 500, statusText: 'Server Error' });

    expect(handledErrors).toHaveLength(1);
  });
});
