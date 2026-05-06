import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ErrorHandler } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { centralHttpErrorInterceptor } from './central-http-error.interceptor';

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

    httpClient.get('/api/processes').subscribe({ error: errorSpy });

    const request = httpTestingController.expectOne('/api/processes');
    request.flush({ message: 'Service unavailable' }, { status: 503, statusText: 'Service Unavailable' });

    expect(handledErrors).toHaveLength(1);
    expect(errorSpy).toHaveBeenCalledWith(handledErrors[0]);
  });
});
