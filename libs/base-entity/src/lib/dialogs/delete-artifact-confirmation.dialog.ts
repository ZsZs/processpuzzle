import { Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { provideTranslocoScope, TranslocoService } from '@jsverse/transloco';

export interface DeleteArtifactConfirmationDialogData {
  titleKey: string;
  contentKey: string;
  contentParams?: Record<string, string>;
  cancelButtonKey: string;
  confirmButtonKey: string;
}

@Component({
  selector: 'app-delete-artifact-confirmation-dialog',
  standalone: true,
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton],
  providers: [provideTranslocoScope('base-entity')],
  template: `
    <h2 mat-dialog-title>{{ t(data.titleKey) }}</h2>
    <mat-dialog-content>{{ t(data.contentKey, data.contentParams) }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button type="button" mat-button [mat-dialog-close]="false">{{ t(data.cancelButtonKey) }}</button>
      <button type="button" mat-raised-button color="warn" [mat-dialog-close]="true">{{ t(data.confirmButtonKey) }}</button>
    </mat-dialog-actions>
  `,
})
export class DeleteArtifactConfirmationDialog {
  protected readonly data = inject<DeleteArtifactConfirmationDialogData>(MAT_DIALOG_DATA);
  private readonly translocoService = inject(TranslocoService);

  protected t(key: string, params?: Record<string, string>): string {
    return this.translocoService.translate(key, params);
  }
}
