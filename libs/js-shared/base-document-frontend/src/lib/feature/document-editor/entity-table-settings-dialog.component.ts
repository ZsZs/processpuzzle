import { Component, inject, signal } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { EntityTableColumn, EntityTableTotal, VALUE_FORMATS, ValueFormat } from '@processpuzzle/widgets';
import { DocumentCollectionPath } from '../report/document-path-catalog';

export interface EntityTableSettings {
  /** The collection path `rows` is bound to — `order.lineItems`. */
  source?: string;
  columns: EntityTableColumn[];
  totals: EntityTableTotal[];
  caption?: string;
}

export interface EntityTableSettingsData {
  collections: DocumentCollectionPath[];
  current: EntityTableSettings;
}

/** A column being edited, with whether it is shown and totalled — the dialog's working copy of one row field. */
interface ColumnDraft extends Required<Pick<EntityTableColumn, 'path' | 'align'>> {
  label: string;
  header: string;
  format: ValueFormat;
  shown: boolean;
  total: boolean;
}

/**
 * Configures an `entity-table` block: which collection its rows are, which of the row's attributes it shows,
 * in what order, under which header, and which columns are summed. The document editor has no generic props
 * panel, and the widget's `columns` are an array of objects the props form cannot edit as rows yet.
 */
@Component({
  selector: 'pp-entity-table-settings-dialog',
  standalone: true,
  imports: [
    MatButton,
    MatCheckbox,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormField,
    MatIcon,
    MatIconButton,
    MatInput,
    MatLabel,
    MatOption,
    MatSelect,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'base_document.document.report.table_settings' | transloco }}</h2>
    <mat-dialog-content class="pp-table-settings">
      <div class="pp-table-settings__row">
        <mat-form-field>
          <mat-label>{{ 'base_document.document.report.rows' | transloco }}</mat-label>
          <mat-select data-testid="table-settings-source" [value]="source()" (valueChange)="onSourceChange($event)">
            @for (collection of data.collections; track collection.path) {
              <mat-option [value]="collection.path">{{ collection.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>{{ 'base_document.document.report.caption' | transloco }}</mat-label>
          <input matInput data-testid="table-settings-caption" [value]="caption()" (input)="caption.set($any($event.target).value)" />
        </mat-form-field>
      </div>

      @for (column of columns(); track column.path; let i = $index; let last = $last) {
        <div class="pp-table-settings__column" [attr.data-testid]="'table-settings-column-' + column.path">
          <mat-checkbox [checked]="column.shown" (change)="patch(i, { shown: $event.checked })">{{ column.label }}</mat-checkbox>
          <mat-form-field subscriptSizing="dynamic">
            <mat-label>{{ 'base_document.document.report.header' | transloco }}</mat-label>
            <input matInput [value]="column.header" [disabled]="!column.shown" (input)="patch(i, { header: $any($event.target).value })" />
          </mat-form-field>
          <mat-form-field subscriptSizing="dynamic">
            <mat-label>{{ 'base_document.document.report.format' | transloco }}</mat-label>
            <mat-select [value]="column.format" [disabled]="!column.shown" (valueChange)="patch(i, { format: $event })">
              @for (option of formats; track option) {
                <mat-option [value]="option">{{ option }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-checkbox [checked]="column.total" [disabled]="!column.shown" (change)="patch(i, { total: $event.checked })">
            {{ 'base_document.document.report.total' | transloco }}
          </mat-checkbox>
          <button mat-icon-button type="button" [disabled]="i === 0" [attr.aria-label]="'base_document.document.report.move_up' | transloco" (click)="move(i, -1)">
            <mat-icon>arrow_upward</mat-icon>
          </button>
          <button mat-icon-button type="button" [disabled]="last" [attr.aria-label]="'base_document.document.report.move_down' | transloco" (click)="move(i, 1)">
            <mat-icon>arrow_downward</mat-icon>
          </button>
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'base_document.document.report.cancel' | transloco }}</button>
      <button mat-flat-button type="button" data-testid="table-settings-save" [disabled]="!source()" (click)="onSave()">
        {{ 'base_document.document.report.apply' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .pp-table-settings {
        display: flex;
        flex-direction: column;
        gap: 8px;
        min-width: 640px;
      }
      .pp-table-settings__row,
      .pp-table-settings__column {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .pp-table-settings__column mat-checkbox:first-child {
        flex: 0 0 180px;
      }
    `,
  ],
})
export class EntityTableSettingsDialogComponent {
  protected readonly data = inject<EntityTableSettingsData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<EntityTableSettingsDialogComponent, EntityTableSettings>>(MatDialogRef);

  protected readonly formats = VALUE_FORMATS;
  protected readonly source = signal<string | undefined>(this.data.current.source);
  protected readonly caption = signal(this.data.current.caption ?? '');
  protected readonly columns = signal<ColumnDraft[]>(draftsFor(this.data.collections, this.data.current));

  /** Another collection has other attributes: start over with all of them shown, in definition order. */
  protected onSourceChange(source: string): void {
    this.source.set(source);
    this.columns.set(draftsFor(this.data.collections, { source, columns: [], totals: [] }));
  }

  protected patch(index: number, change: Partial<ColumnDraft>): void {
    this.columns.update((columns) => columns.map((column, i) => (i === index ? { ...column, ...change } : column)));
  }

  protected move(index: number, offset: number): void {
    this.columns.update((columns) => {
      const reordered = [...columns];
      const [moved] = reordered.splice(index, 1);
      reordered.splice(index + offset, 0, moved);
      return reordered;
    });
  }

  protected onSave(): void {
    const shown = this.columns().filter((column) => column.shown);
    this.dialogRef.close({
      source: this.source(),
      caption: this.caption() || undefined,
      columns: shown.map(({ path, header, format, align }) => ({ path, header, format, align })),
      totals: shown.filter((column) => column.total).map((column) => ({ path: column.path, aggregate: 'sum' as const })),
    });
  }
}

/**
 * The configured columns first, in their order, then every other field of the collection, unshown — so a
 * column dropped earlier can be brought back. With nothing configured, every field is shown.
 */
function draftsFor(collections: DocumentCollectionPath[], current: EntityTableSettings): ColumnDraft[] {
  const fields = collections.find((collection) => collection.path === current.source)?.rowFields ?? [];
  const configured = current.columns ?? [];
  const totalled = new Set((current.totals ?? []).map((total) => total.path));
  const showAll = configured.length === 0;
  const byPath = new Map(fields.map((field) => [field.path, field]));
  const drafts: ColumnDraft[] = configured.map((column) => ({
    path: column.path,
    label: byPath.get(column.path)?.label ?? column.path,
    header: column.header ?? '',
    format: column.format ?? 'text',
    align: column.align ?? 'start',
    shown: true,
    total: totalled.has(column.path),
  }));
  for (const field of fields) {
    if (configured.some((column) => column.path === field.path)) continue;
    const numeric = field.format === 'number';
    drafts.push({ path: field.path, label: field.label, header: field.label, format: field.format, align: numeric ? 'end' : 'start', shown: showAll, total: false });
  }
  return drafts;
}
