import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MAT_SNACK_BAR_DATA, MatSnackBarRef } from '@angular/material/snack-bar';

export interface ErrorSnackbarData {
  message: string;
}

@Component({
  selector: 'pp-error-snackbar',
  imports: [MatButton, MatIcon],
  template: `
    <div class="error-snackbar" role="alert">
      <mat-icon class="error-snackbar__icon" aria-hidden="true">error</mat-icon>
      <span class="error-snackbar__text">{{ data.message }}</span>
      <button mat-button type="button" (click)="dismiss()">Close</button>
    </div>
  `,
  styles: [
    `
      .error-snackbar {
        align-items: center;
        display: flex;
        gap: 12px;
      }

      .error-snackbar__icon {
        flex: 0 0 auto;
      }

      .error-snackbar__text {
        flex: 1 1 auto;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ErrorSnackbarComponent {
  readonly data = inject<ErrorSnackbarData>(MAT_SNACK_BAR_DATA);
  private readonly snackBarRef = inject(MatSnackBarRef<ErrorSnackbarComponent>);

  dismiss(): void {
    this.snackBarRef.dismiss();
  }
}
