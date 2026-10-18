import { Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { VALUE_FORMATS, ValueFormat } from '@processpuzzle/widgets';
import { DocumentFieldPath } from '../report/document-path-catalog';

export interface DataFieldDialogData {
  fields: DocumentFieldPath[];
}

export interface DataFieldChoice {
  path: string;
  format: ValueFormat;
}

/** Picks the value a `dataField` node prints: one of the ports' paths, and the format to print it in. */
@Component({
  selector: 'pp-data-field-dialog',
  standalone: true,
  imports: [MatButton, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle, MatFormField, MatLabel, MatOption, MatSelect, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ 'base_document.document.report.insert_field' | transloco }}</h2>
    <mat-dialog-content class="pp-data-field-dialog">
      <mat-form-field>
        <mat-label>{{ 'base_document.document.report.field' | transloco }}</mat-label>
        <mat-select data-testid="data-field-path" [value]="path()" (valueChange)="onPathChange($event)">
          @for (field of data.fields; track field.path) {
            <mat-option [value]="field.path">{{ field.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label>{{ 'base_document.document.report.format' | transloco }}</mat-label>
        <mat-select data-testid="data-field-format" [value]="format()" (valueChange)="format.set($event)">
          @for (option of formats; track option) {
            <mat-option [value]="option">{{ option }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'base_document.document.report.cancel' | transloco }}</button>
      <button mat-flat-button type="button" data-testid="data-field-insert" [disabled]="!path()" (click)="onInsert()">
        {{ 'base_document.document.report.insert' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .pp-data-field-dialog {
        display: flex;
        flex-direction: column;
        min-width: 360px;
      }
    `,
  ],
})
export class DataFieldDialogComponent {
  protected readonly data = inject<DataFieldDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<DataFieldDialogComponent, DataFieldChoice>>(MatDialogRef);

  protected readonly formats = VALUE_FORMATS;
  protected readonly path = signal<string | undefined>(undefined);
  protected readonly format = signal<ValueFormat>('text');

  /** A new path brings the format its value kind suggests; the author can still change it after. */
  protected onPathChange(path: string): void {
    this.path.set(path);
    this.format.set(this.data.fields.find((field) => field.path === path)?.format ?? 'text');
  }

  protected onInsert(): void {
    const path = this.path();
    if (path) this.dialogRef.close({ path, format: this.format() });
  }
}
