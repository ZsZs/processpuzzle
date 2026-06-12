import { HttpErrorResponse } from '@angular/common/http';
import { ErrorHandler } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NGXLogger } from 'ngx-logging-kit';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CentralErrorHandler } from './central-error-handler';
import { ERROR_MESSAGE_REPORTER, ErrorMessageReporter } from './error-message-reporter';
import { provideCentralErrorHandler } from './provide-central-error-handler';

describe('CentralErrorHandler', () => {
  let errorHandler: CentralErrorHandler;
  let logger: Pick<NGXLogger, 'error' | 'fatal' | 'warn'>;

  beforeEach(() => {
    logger = {
      error: vi.fn(),
      fatal: vi.fn(),
      warn: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [CentralErrorHandler, { provide: NGXLogger, useValue: logger }],
    });
    errorHandler = TestBed.inject(CentralErrorHandler);
  });

  it('logs unhandled Error instances as fatal errors', () => {
    const error = new Error('Unexpected failure');

    errorHandler.handleError(error);

    expect(logger.fatal).toHaveBeenCalledWith('Unexpected failure', error);
  });

  it('logs unwrapped promise rejections as fatal errors', () => {
    const error = new Error('Rejected failure');

    errorHandler.handleError({ rejection: error });

    expect(logger.fatal).toHaveBeenCalledWith('Rejected failure', error);
  });

  it('logs HTTP errors as errors with request context', () => {
    const error = new HttpErrorResponse({
      status: 503,
      statusText: 'Service Unavailable',
      url: '/api/processes',
    });

    errorHandler.handleError(error);

    expect(logger.error).toHaveBeenCalledWith('HTTP 503 Service Unavailable /api/processes', error);
  });

  it('shows Error messages through the optional error message reporter', () => {
    const reporter: ErrorMessageReporter = { showErrorMessage: vi.fn() };
    const error = new Error('Unexpected failure');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [CentralErrorHandler, { provide: NGXLogger, useValue: logger }, { provide: ERROR_MESSAGE_REPORTER, useValue: reporter }],
    });

    TestBed.inject(CentralErrorHandler).handleError(error);

    expect(reporter.showErrorMessage).toHaveBeenCalledWith('Unexpected failure', error);
  });

  it('shows HTTP response body messages when available', () => {
    const reporter: ErrorMessageReporter = { showErrorMessage: vi.fn() };
    const error = new HttpErrorResponse({
      error: { message: 'Backend validation failed' },
      status: 400,
      statusText: 'Bad Request',
      url: '/api/processes',
    });

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [CentralErrorHandler, { provide: NGXLogger, useValue: logger }, { provide: ERROR_MESSAGE_REPORTER, useValue: reporter }],
    });

    TestBed.inject(CentralErrorHandler).handleError(error);

    expect(reporter.showErrorMessage).toHaveBeenCalledWith('Backend validation failed', error);
  });

  it('falls back to a generic fatal log for non-error values', () => {
    errorHandler.handleError('failure');

    expect(logger.fatal).toHaveBeenCalledWith('Unhandled exception.', 'failure');
  });
});

describe('provideCentralErrorHandler', () => {
  it('registers CentralErrorHandler as the Angular ErrorHandler', () => {
    TestBed.configureTestingModule({
      providers: [provideCentralErrorHandler(), { provide: NGXLogger, useValue: { error: vi.fn(), fatal: vi.fn(), warn: vi.fn() } }],
    });

    expect(TestBed.inject(ErrorHandler)).toBeInstanceOf(CentralErrorHandler);
  });
});
