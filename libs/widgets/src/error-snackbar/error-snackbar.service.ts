import { EnvironmentProviders, Injectable, makeEnvironmentProviders, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ERROR_MESSAGE_REPORTER, ErrorMessageReporter } from '@processpuzzle/util';
import { ErrorSnackbarComponent, ErrorSnackbarData } from './error-snackbar.component';

@Injectable()
export class ErrorSnackbarService implements ErrorMessageReporter {
  private readonly snackBar = inject(MatSnackBar);

  showErrorMessage(message: string): void {
    this.snackBar.openFromComponent<ErrorSnackbarComponent, ErrorSnackbarData>(ErrorSnackbarComponent, {
      data: { message },
      duration: 5000,
      horizontalPosition: 'center',
      panelClass: ['error-snackbar'],
      verticalPosition: 'bottom',
    });
  }
}

export function provideErrorSnackbar(): EnvironmentProviders {
  return makeEnvironmentProviders([{ provide: ERROR_MESSAGE_REPORTER, useClass: ErrorSnackbarService }]);
}
