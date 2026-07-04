import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { RsqlQueryEditorComponent } from './rsql-query-editor.component';

export interface RsqlEditorDialogData {
  initialQuery: string;
}

@Component({
  selector: 'base-entity-rsql-editor-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton, RsqlQueryEditorComponent, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })],
  template: `
    <ng-container *transloco="let t">
      <h2 mat-dialog-title>{{ t('base_entity.rsql_editor_dialog.title') }}</h2>
      <mat-dialog-content class="editor-content">
        <pp-rsql-query-editor [formControl]="queryControl" (validityChange)="isValid.set($event)"> </pp-rsql-query-editor>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button type="button" mat-button [mat-dialog-close]="null">{{ t('base_entity.rsql_editor_dialog.cancel_button') }}</button>
        <button type="button" mat-raised-button color="primary" [disabled]="!isValid()" [mat-dialog-close]="queryControl.value">{{ t('base_entity.rsql_editor_dialog.apply_button') }}</button>
      </mat-dialog-actions>
    </ng-container>
  `,
  styles: [
    `
      .editor-content {
        min-width: 520px;
      }
    `,
  ],
})
export class RsqlEditorDialog {
  private readonly data = inject<RsqlEditorDialogData>(MAT_DIALOG_DATA);
  protected readonly queryControl = new FormControl<string>(this.data.initialQuery ?? '', { nonNullable: true });
  protected readonly isValid = signal<boolean>(true);
}
