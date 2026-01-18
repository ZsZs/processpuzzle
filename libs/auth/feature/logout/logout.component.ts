import { Component, inject, signal } from '@angular/core';
import { MatDialogActions, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { NavigateBackService } from '@processpuzzle/widgets';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';

@Component({
  selector: 'pp-logout',
  template: `
    <div class="logout-dialog">
      <ng-container *transloco="let t">
        <h2 mat-dialog-title>{{ t('auth.logout-dialog.title') }}</h2>
        <mat-dialog-content>{{ t('auth.logout-dialog.content') }}</mat-dialog-content>
        <mat-dialog-actions align="end">
          <button mat-button (click)="onCancel()">{{ t('auth.logout-dialog.cancel_button') }}</button>
          <button mat-raised-button color="primary" (click)="onLogout()" [disabled]="isLoading()">{{ t('auth.logout-dialog.logout_button') }}</button>
        </mat-dialog-actions>
      </ng-container>
    </div>
  `,
  styles: [
    `
      .logout-dialog {
        padding: 1rem;
      }

      mat-dialog-actions {
        gap: 0.5rem;
      }

      mat-dialog-content {
        margin: 1rem 0;
      }
    `,
  ],
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatButton, TranslocoDirective],
  providers: [provideTranslocoScope('auth')],
})
export class LogoutComponent {
  private readonly authService = inject(AUTHENTICATION_SERVICE);
  private readonly navigateBackService = inject(NavigateBackService);
  protected isLoading = signal(false);

  onCancel() {
    this.navigateBackService.goBack();
  }

  async onLogout(): Promise<void> {
    try {
      this.isLoading.set(true);
      this.navigateBackService.getRouteStack().pop();
      await this.authService.logout(this.navigateBackService.getRouteStack().pop());
      this.navigateBackService.goBack();
    } catch (error) {
      console.error('Error during logout:', error);
    } finally {
      this.isLoading.set(false);
    }
  }
}
