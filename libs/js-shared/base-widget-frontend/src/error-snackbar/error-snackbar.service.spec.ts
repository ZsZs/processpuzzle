import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ERROR_MESSAGE_REPORTER } from '@processpuzzle/util';
import { describe, expect, it, vi } from 'vitest';
import { ErrorSnackbarComponent } from './error-snackbar.component';
import { ErrorSnackbarService, provideErrorSnackbar } from './error-snackbar.service';

describe('ErrorSnackbarService', () => {
  it('opens the error message component in a snackbar', () => {
    const snackBar = { openFromComponent: vi.fn() };
    const service = TestBed.configureTestingModule({
      providers: [ErrorSnackbarService, { provide: MatSnackBar, useValue: snackBar }],
    }).inject(ErrorSnackbarService);

    service.showErrorMessage('Unexpected failure');

    expect(snackBar.openFromComponent).toHaveBeenCalledWith(ErrorSnackbarComponent, {
      data: { message: 'Unexpected failure' },
      duration: 5000,
      horizontalPosition: 'center',
      panelClass: ['error-snackbar'],
      verticalPosition: 'bottom',
    });
  });
});

describe('provideErrorSnackbar', () => {
  it('registers the snackbar service as the central error message reporter', () => {
    TestBed.configureTestingModule({
      providers: [provideErrorSnackbar(), { provide: MatSnackBar, useValue: { openFromComponent: vi.fn() } }],
    });

    expect(TestBed.inject(ERROR_MESSAGE_REPORTER)).toBeInstanceOf(ErrorSnackbarService);
  });
});
