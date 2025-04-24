import { Component, inject } from '@angular/core';
import { MatDialogActions, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { AuthService } from '../domain/auth.service';
import { NavigateBackService } from '@processpuzzle/widgets';

@Component({
  selector: 'pp-logout',
  template: `
    <div class="logout-dialog">
      <h2 mat-dialog-title>Confirm Logout</h2>
      <mat-dialog-content> Are you sure you want to log out?</mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button (click)="onCancel()">Cancel</button>
        <button mat-raised-button color="primary" (click)="onLogout()" [disabled]="isLoading">Logout</button>
      </mat-dialog-actions>
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
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatButton],
})
export class LogoutComponent {
  private readonly authService = inject(AuthService);
  private readonly navigateBackService = inject(NavigateBackService);
  protected isLoading = false;

  onCancel() {
    this.navigateBackService.goBack();
  }

  async onLogout(): Promise<void> {
    try {
      this.isLoading = true;
      await this.authService.signOut();
      this.navigateBackService.goBack();
    } catch (error) {
      console.error('Error during logout:', error);
    } finally {
      this.isLoading = false;
    }
  }

  protected readonly oncancel = oncancel;
}
