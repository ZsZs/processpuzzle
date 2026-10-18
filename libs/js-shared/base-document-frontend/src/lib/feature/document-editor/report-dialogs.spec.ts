import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DocumentCollectionPath } from '../report/document-path-catalog';
import { DataFieldDialogComponent } from './data-field-dialog.component';
import { EntityTableSettings, EntityTableSettingsDialogComponent } from './entity-table-settings-dialog.component';

const collections: DocumentCollectionPath[] = [
  {
    path: 'order.lineItems',
    label: 'Line items',
    rowFields: [
      { path: 'productName', label: 'Product', format: 'text' },
      { path: 'quantity', label: 'Quantity', format: 'number' },
      { path: 'unitPrice', label: 'Unit price', format: 'number' },
    ],
  },
  { path: 'openOrders', label: 'Open orders', rowFields: [{ path: 'orderNumber', label: 'Order #', format: 'text' }] },
];

function create<T>(component: Type<T>, data: unknown) {
  const close = vi.fn();
  TestBed.configureTestingModule({
    providers: [provideTranslocoTesting({ translations: {} }), { provide: MAT_DIALOG_DATA, useValue: data }, { provide: MatDialogRef, useValue: { close } }],
  });
  const fixture = TestBed.createComponent(component);
  fixture.detectChanges();
  return { component: fixture.componentInstance as T & Record<string, never>, close, fixture };
}

describe('DataFieldDialogComponent', () => {
  it('suggests the chosen path’s format and closes with both', () => {
    const { component, close } = create(DataFieldDialogComponent, {
      fields: [
        { path: 'order.total', label: 'Total', format: 'number' },
        { path: 'order.unknown', label: 'Unknown', format: 'text' },
      ],
    });
    const dialog = component as unknown as { onPathChange(path: string): void; onInsert(): void; format: { (): string; set(value: string): void } };

    dialog.onInsert();
    expect(close).not.toHaveBeenCalled();

    dialog.onPathChange('order.total');
    expect(dialog.format()).toBe('number');
    dialog.format.set('currency:EUR');
    dialog.onInsert();
    expect(close).toHaveBeenCalledWith({ path: 'order.total', format: 'currency:EUR' });

    dialog.onPathChange('nowhere');
    expect(dialog.format()).toBe('text');
  });
});

interface SettingsDialog {
  columns(): { path: string; header: string; shown: boolean; total: boolean; align: string; label: string }[];
  source(): string | undefined;
  caption: { set(value: string): void };
  onSourceChange(source: string): void;
  patch(index: number, change: object): void;
  move(index: number, offset: number): void;
  onSave(): void;
}

describe('EntityTableSettingsDialogComponent', () => {
  let dialog: SettingsDialog;
  let close: ReturnType<typeof vi.fn>;

  function open(current: EntityTableSettings) {
    const created = create(EntityTableSettingsDialogComponent, { collections, current });
    dialog = created.component as unknown as SettingsDialog;
    close = created.close;
  }

  describe('with nothing configured', () => {
    beforeEach(() => open({ columns: [], totals: [] }));

    it('has no columns until a collection is chosen, then shows all of its fields', () => {
      expect(dialog.columns()).toEqual([]);

      dialog.onSourceChange('order.lineItems');

      expect(dialog.columns().map((column) => [column.path, column.shown, column.align])).toEqual([
        ['productName', true, 'start'],
        ['quantity', true, 'end'],
        ['unitPrice', true, 'end'],
      ]);
    });

    it('saves the shown columns in their order, the totalled ones as sums, and the caption', () => {
      dialog.onSourceChange('order.lineItems');
      dialog.patch(0, { shown: false });
      dialog.patch(1, { total: true, header: 'Qty' });
      dialog.move(2, -1);
      dialog.caption.set('Items');
      dialog.onSave();

      expect(close).toHaveBeenCalledWith({
        source: 'order.lineItems',
        caption: 'Items',
        columns: [
          { path: 'unitPrice', header: 'Unit price', format: 'number', align: 'end' },
          { path: 'quantity', header: 'Qty', format: 'number', align: 'end' },
        ],
        totals: [{ path: 'quantity', aggregate: 'sum' }],
      });
    });
  });

  it('keeps the configured columns first and offers the rest unshown', () => {
    open({
      source: 'order.lineItems',
      columns: [{ path: 'quantity', header: 'Qty' }, { path: 'gone' }],
      totals: [{ path: 'quantity', aggregate: 'sum' }],
      caption: 'Lines',
    });

    expect(dialog.columns().map((column) => [column.path, column.label, column.shown, column.total])).toEqual([
      ['quantity', 'Quantity', true, true],
      ['gone', 'gone', true, false],
      ['productName', 'Product', false, false],
      ['unitPrice', 'Unit price', false, false],
    ]);

    dialog.caption.set('');
    dialog.onSave();
    expect(close.mock.calls[0][0].caption).toBeUndefined();
  });

  it('reads an unknown source as one without fields', () => {
    open({ source: 'nowhere', columns: [], totals: [] });

    expect(dialog.columns()).toEqual([]);
  });
});
