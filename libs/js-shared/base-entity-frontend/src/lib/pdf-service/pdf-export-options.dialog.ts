import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDialogActions, MatDialogClose, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { MatOption, MatSelect } from '@angular/material/select';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import type { PdfExportOptions } from './pdf-export.types';

export type PdfExportDialogResult = Pick<PdfExportOptions, 'orientation' | 'pageSize' | 'includeFooter'>;

/**
 * Asks the user for the few PDF layout choices we expose — orientation, page size and
 * whether to print a footer. Deliberately narrow: this is not a full layout editor.
 *
 * Closes with a {@link PdfExportDialogResult} on Export, or `undefined` on Cancel.
 */
@Component({
  selector: 'base-entity-pdf-export-options-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatDialogTitle, MatDialogContent, MatDialogActions, MatDialogClose, MatButton, MatCheckbox, MatFormField, MatLabel, MatRadioGroup, MatRadioButton, MatSelect, MatOption, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })],
  template: `
    <ng-container *transloco="let t">
      <h2 mat-dialog-title>{{ t('base_entity.pdf_export.dialog_title') }}</h2>
      <mat-dialog-content>
        <fieldset class="pdf-export-field">
          <legend>{{ t('base_entity.pdf_export.orientation_label') }}</legend>
          <mat-radio-group [(ngModel)]="orientation" aria-labelledby="pdf-orientation">
            <mat-radio-button value="portrait">{{ t('base_entity.pdf_export.orientation_portrait') }}</mat-radio-button>
            <mat-radio-button value="landscape">{{ t('base_entity.pdf_export.orientation_landscape') }}</mat-radio-button>
          </mat-radio-group>
        </fieldset>

        <mat-form-field class="pdf-export-field" subscriptSizing="dynamic">
          <mat-label>{{ t('base_entity.pdf_export.page_size_label') }}</mat-label>
          <mat-select [(ngModel)]="pageSize">
            <mat-option value="a4">A4</mat-option>
            <mat-option value="a3">A3</mat-option>
            <mat-option value="letter">Letter</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-checkbox class="pdf-export-field" [(ngModel)]="includeFooter">{{ t('base_entity.pdf_export.include_footer_label') }}</mat-checkbox>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button type="button" mat-button [mat-dialog-close]="undefined">{{ t('base_entity.pdf_export.cancel_button') }}</button>
        <button type="button" mat-raised-button color="primary" (click)="onExport()">{{ t('base_entity.pdf_export.export_button') }}</button>
      </mat-dialog-actions>
    </ng-container>
  `,
  styles: [
    `
      .pdf-export-field {
        display: block;
        margin-bottom: 16px;
      }
      mat-radio-group {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      mat-form-field {
        width: 100%;
      }
    `,
  ],
})
export class PdfExportOptionsDialog {
  private readonly dialogRef = inject<MatDialogRef<PdfExportOptionsDialog, PdfExportDialogResult | undefined>>(MatDialogRef);

  protected readonly orientation = signal<'portrait' | 'landscape'>('portrait');
  protected readonly pageSize = signal<'a4' | 'a3' | 'letter'>('a4');
  protected readonly includeFooter = signal(true);

  protected onExport(): void {
    this.dialogRef.close({
      orientation: this.orientation(),
      pageSize: this.pageSize(),
      includeFooter: this.includeFooter(),
    });
  }
}
