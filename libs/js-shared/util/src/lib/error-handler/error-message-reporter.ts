import { InjectionToken } from '@angular/core';

export interface ErrorMessageReporter {
  showErrorMessage(message: string, error: unknown): void;
}

export const ERROR_MESSAGE_REPORTER = new InjectionToken<ErrorMessageReporter>('ERROR_MESSAGE_REPORTER');
