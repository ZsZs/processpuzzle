import { Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';

/** Asks before a document is deleted from a content book — deleted for good, not only taken out of the book. */
@Component({
  selector: 'pp-content-book-confirm-delete-dialog',
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ 'base_document.content_book.widget.confirm_delete_title' | transloco }}</h2>
    <mat-dialog-content>{{ 'base_document.content_book.widget.confirm_delete_text' | transloco: { title: data.title } }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button type="button" mat-button data-testid="content-book-delete-cancel" [mat-dialog-close]="false">{{ 'base_document.content_book.widget.cancel' | transloco }}</button>
      <button type="button" mat-raised-button data-testid="content-book-delete-confirm" [mat-dialog-close]="true">{{ 'base_document.content_book.widget.delete' | transloco }}</button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDeleteDocumentDialog {
  protected readonly data = inject<{ title: string }>(MAT_DIALOG_DATA);
}
