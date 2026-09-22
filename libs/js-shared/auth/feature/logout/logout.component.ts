import { Component, inject, signal } from '@angular/core';
import { MatDialogActions, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { NavigateBackService } from '@processpuzzle/util';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { AUTHENTICATION_SERVICE, LOGOUT_REDIRECT_URL } from '@processpuzzle/auth/domain';

@Component({
  selector: 'pp-logout',
  template: `
    <div class="logout-dialog">
      <ng-container *transloco="let t">
        <h2 mat-dialog-title>{{ t('auth.logout_dialog.title') }}</h2>
        <mat-dialog-content>{{ t('auth.logout_dialog.content') }}</mat-dialog-content>
        <mat-dialog-actions align="end">
          <button mat-button (click)="onCancel()">{{ t('auth.logout_dialog.cancel_button') }}</button>
          <button mat-raised-button color="primary" (click)="onLogout()" [disabled]="isLoading()">{{ t('auth.logout_dialog.logout_button') }}</button>
        </mat-dialog-actions>
      </ng-container>
    </div>
  `,
  styles: [
    `
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
  private readonly logoutRedirectUrl = inject(LOGOUT_REDIRECT_URL, { optional: true });
  protected isLoading = signal(false);

  onCancel() {
    this.navigateBackService.goBack();
  }

  async onLogout(): Promise<void> {
    try {
      this.isLoading.set(true);
      this.navigateBackService.getRouteStack().pop();
      const redirectUrl = this.logoutRedirectUrl ?? this.navigateBackService.getRouteStack().pop();
      await this.authService.logout(redirectUrl);
      // Keycloak normally replaces this document with the configured redirect. Retain the historic
      // in-app back navigation only where no application has supplied an explicit public landing.
      if (!this.logoutRedirectUrl) this.navigateBackService.goBack();
    } catch (error) {
      console.error('Error during logout:', error);
    } finally {
      this.isLoading.set(false);
    }
  }
}
