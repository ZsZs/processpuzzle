import { ErrorHandler, Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class GlobalErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    const chunkFailedMessage = /Loading chunk \d+ failed/;

    if (error instanceof Error && chunkFailedMessage.test(error.message)) {
      window.location.reload();
    }
    console.error(error);
  }
}
