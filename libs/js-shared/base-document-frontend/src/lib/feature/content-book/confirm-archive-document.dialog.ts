import { Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';

/** Asks before a document is archived — a snapshot is stored and the document can no longer be edited. */
@Component({
  selector: 'pp-content-book-confirm-archive-dialog',
  imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ 'base_document.content_book.widget.confirm_archive_title' | transloco }}</h2>
    <mat-dialog-content>{{ 'base_document.content_book.widget.confirm_archive_text' | transloco: { title: data.title } }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button type="button" mat-button data-testid="content-book-archive-cancel" [mat-dialog-close]="false">{{ 'base_document.content_book.widget.cancel' | transloco }}</button>
      <button type="button" mat-raised-button data-testid="content-book-archive-confirm" [mat-dialog-close]="true">{{ 'base_document.content_book.widget.archive' | transloco }}</button>
    </mat-dialog-actions>
  `,
})
export class ConfirmArchiveDocumentDialog {
  protected readonly data = inject<{ title: string }>(MAT_DIALOG_DATA);
}
