import { HttpErrorResponse } from '@angular/common/http';
import { ErrorHandler, Injectable, inject } from '@angular/core';
import { NGXLogger } from 'ngx-logging-kit';
import { ERROR_MESSAGE_REPORTER } from './error-message-reporter';

type ErrorRecord = Record<string, unknown>;

@Injectable()
export class CentralErrorHandler implements ErrorHandler {
  private readonly logger = inject(NGXLogger);
  private readonly errorMessageReporter = inject(ERROR_MESSAGE_REPORTER, { optional: true });

  handleError(error: unknown): void {
    try {
      const unwrappedError = this.unwrapError(error);
      this.logError(unwrappedError);
      this.showErrorMessage(unwrappedError);
    } catch (loggingError) {
      console.error('Failed to log unhandled exception.', loggingError, error);
    }
  }

  private logError(error: unknown): void {
    if (this.isChunkLoadError(error)) {
      this.logger.warn('Application chunk failed to load. Reloading application.', error);
      window.location.reload();
      return;
    }

    if (error instanceof HttpErrorResponse) {
      this.logger.error(this.formatHttpError(error), error);
      return;
    }

    if (error instanceof Error) {
      this.logger.fatal(error.message || 'Unhandled exception.', error);
      return;
    }

    this.logger.fatal('Unhandled exception.', error);
  }

  private unwrapError(error: unknown): unknown {
    if (!this.isRecord(error)) {
      return error;
    }

    return error['rejection'] ?? error['ngOriginalError'] ?? error['originalError'] ?? error;
  }

  private isChunkLoadError(error: unknown): boolean {
    const message = this.getErrorMessage(error);
    return /Loading chunk [\d]+ failed|ChunkLoadError/i.test(message);
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }

    if (this.isRecord(error) && typeof error['message'] === 'string') {
      return error['message'];
    }

    return String(error);
  }

  private getDisplayMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return this.getHttpErrorMessage(error);
    }

    if (error instanceof Error) {
      return error.message || 'Unhandled exception.';
    }

    if (this.isRecord(error) && typeof error['message'] === 'string') {
      return error['message'];
    }

    return 'Unhandled exception.';
  }

  private getHttpErrorMessage(error: HttpErrorResponse): string {
    const responseError = error.error;

    if (this.isRecord(responseError) && typeof responseError['message'] === 'string') {
      return responseError['message'];
    }

    if (typeof responseError === 'string' && responseError.length > 0) {
      return responseError;
    }

    return error.message || this.formatHttpError(error);
  }

  private formatHttpError(error: HttpErrorResponse): string {
    const url = error.url ? ` ${error.url}` : '';
    const statusText = error.statusText ? ` ${error.statusText}` : '';
    return `HTTP ${error.status}${statusText}${url}`;
  }

  private isRecord(value: unknown): value is ErrorRecord {
    return typeof value === 'object' && value !== null;
  }

  private showErrorMessage(error: unknown): void {
    this.errorMessageReporter?.showErrorMessage(this.getDisplayMessage(error), error);
  }
}
