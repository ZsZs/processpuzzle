import { Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { provideTranslocoScope, TranslocoService } from '@jsverse/transloco';

/**
 * Confirmation for a destructive action. Fully key-driven, so the same dialog serves an artifact removal and
 * the deletion of a component entity — the caller supplies the translation keys.
 */
export interface DeleteConfirmationDialogData {
  titleKey: string;
  contentKey: string;
  contentParams?: Record<string, string>;
  cancelButtonKey: string;
  confirmButtonKey: string;
}

@Component({
  selector: 'app-delete-confirmation-dialog',
  standalone: true,
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton],
  providers: [provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })],
  template: `
    <h2 mat-dialog-title>{{ t(data.titleKey) }}</h2>
    <mat-dialog-content>{{ t(data.contentKey, data.contentParams) }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <!-- The labels are translated, so the test ids are what an e2e test can address the buttons by. -->
      <button type="button" mat-button data-testid="delete-confirmation-cancel" [mat-dialog-close]="false">{{ t(data.cancelButtonKey) }}</button>
      <button type="button" mat-raised-button color="warn" data-testid="delete-confirmation-confirm" [mat-dialog-close]="true">{{ t(data.confirmButtonKey) }}</button>
    </mat-dialog-actions>
  `,
})
export class DeleteConfirmationDialog {
  protected readonly data = inject<DeleteConfirmationDialogData>(MAT_DIALOG_DATA);
  private readonly translocoService = inject(TranslocoService);

  protected t(key: string, params?: Record<string, string>): string {
    return this.translocoService.translate(key, params);
  }
}
