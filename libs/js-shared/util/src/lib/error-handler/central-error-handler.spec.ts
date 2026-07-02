import { HttpErrorResponse } from '@angular/common/http';
import { ErrorHandler } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NGXLogger } from 'ngx-logging-kit';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
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

    expect(logger.error).toHaveBeenCalledWith('HTTP 503 /api/processes', error);
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

  it('reloads the application on chunk load errors', () => {
    const reload = vi.fn();
    vi.stubGlobal('location', { reload });
    const error = new Error('Loading chunk 42 failed');

    errorHandler.handleError(error);

    expect(logger.warn).toHaveBeenCalledWith('Application chunk failed to load. Reloading application.', error);
    expect(reload).toHaveBeenCalled();
  });

  it('reloads the application on ChunkLoadError messages', () => {
    const reload = vi.fn();
    vi.stubGlobal('location', { reload });
    const error = new Error('ChunkLoadError: dynamic import failed');

    errorHandler.handleError(error);

    expect(logger.warn).toHaveBeenCalled();
    expect(reload).toHaveBeenCalled();
  });

  it('swallows logging failures and reports them via console.error', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const loggingFailure = new Error('logger crashed');
    logger.fatal = vi.fn(() => {
      throw loggingFailure;
    });
    const original = new Error('original failure');

    expect(() => errorHandler.handleError(original)).not.toThrow();
    expect(consoleError).toHaveBeenCalledWith('Failed to log unhandled exception.', loggingFailure, original);
  });

  it('shows record-shaped errors that expose a message property', () => {
    const reporter: ErrorMessageReporter = { showErrorMessage: vi.fn() };
    const error = { message: 'plain object failure' };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [CentralErrorHandler, { provide: NGXLogger, useValue: logger }, { provide: ERROR_MESSAGE_REPORTER, useValue: reporter }],
    });

    TestBed.inject(CentralErrorHandler).handleError(error);

    expect(reporter.showErrorMessage).toHaveBeenCalledWith('plain object failure', error);
  });

  it('shows a generic display message when the error carries no message', () => {
    const reporter: ErrorMessageReporter = { showErrorMessage: vi.fn() };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [CentralErrorHandler, { provide: NGXLogger, useValue: logger }, { provide: ERROR_MESSAGE_REPORTER, useValue: reporter }],
    });

    TestBed.inject(CentralErrorHandler).handleError(42);

    expect(reporter.showErrorMessage).toHaveBeenCalledWith('Unhandled exception.', 42);
  });

  it('shows string bodies returned by HTTP responses', () => {
    const reporter: ErrorMessageReporter = { showErrorMessage: vi.fn() };
    const error = new HttpErrorResponse({
      error: 'Backend crashed',
      status: 500,
      url: '/api/processes',
    });

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [CentralErrorHandler, { provide: NGXLogger, useValue: logger }, { provide: ERROR_MESSAGE_REPORTER, useValue: reporter }],
    });

    TestBed.inject(CentralErrorHandler).handleError(error);

    expect(reporter.showErrorMessage).toHaveBeenCalledWith('Backend crashed', error);
  });

  it('falls back to the HttpErrorResponse message when the body carries none', () => {
    const reporter: ErrorMessageReporter = { showErrorMessage: vi.fn() };
    const error = new HttpErrorResponse({
      error: null,
      status: 500,
      url: '/api/processes',
    });

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [CentralErrorHandler, { provide: NGXLogger, useValue: logger }, { provide: ERROR_MESSAGE_REPORTER, useValue: reporter }],
    });

    TestBed.inject(CentralErrorHandler).handleError(error);

    const [message, receivedError] = (reporter.showErrorMessage as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(typeof message).toBe('string');
    expect(message.length).toBeGreaterThan(0);
    expect(receivedError).toBe(error);
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
