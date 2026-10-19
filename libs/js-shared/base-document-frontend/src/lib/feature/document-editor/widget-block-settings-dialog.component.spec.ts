import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WidgetBlockSettingsData, WidgetBlockSettingsDialogComponent } from './widget-block-settings-dialog.component';

type DialogInternals = {
  fields: { name: string; kind: string; options: string[] }[];
  setProp(name: string, value: unknown): void;
  setJson(name: string, text: string): void;
  setBinding(port: string, path: string): void;
  valid(): boolean;
  onSave(): void;
};

const SCHEMA = {
  type: 'object',
  properties: {
    caption: { type: 'string', title: 'Caption', description: 'Shown above' },
    size: { type: 'integer' },
    bold: { type: 'boolean' },
    align: { type: 'string', enum: ['start', 'end'] },
    items: { type: 'array' },
  },
};

describe('WidgetBlockSettingsDialogComponent', () => {
  let close: ReturnType<typeof vi.fn>;
  let fixture: ComponentFixture<WidgetBlockSettingsDialogComponent>;

  function open(data: Partial<WidgetBlockSettingsData> = {}): DialogInternals {
    close = vi.fn();
    TestBed.configureTestingModule({
      imports: [WidgetBlockSettingsDialogComponent],
      providers: [
        provideTranslocoTesting({ translations: { en: {} } }),
        { provide: MAT_DIALOG_DATA, useValue: { name: 'Sample', inputPorts: [{ name: 'rows', description: 'The rows' }], props: {}, inputBindings: {}, paths: ['order.lineItems'], ...data } },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    fixture = TestBed.createComponent(WidgetBlockSettingsDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance as unknown as DialogInternals;
  }

  const control = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);

  beforeEach(() => TestBed.resetTestingModule());

  it('has a control per described prop, picked by its type', () => {
    const dialog = open({ schema: SCHEMA });

    expect(dialog.fields.map((field) => [field.name, field.kind])).toEqual([
      ['caption', 'text'],
      ['size', 'number'],
      ['bold', 'boolean'],
      ['align', 'enum'],
      ['items', 'json'],
    ]);
    expect(control('widget-prop-caption')).not.toBeNull();
    expect(control('widget-prop-items')).not.toBeNull();
    expect(control('widget-binding-rows')).not.toBeNull();
  });

  it('returns edited props and bindings, leaving out empty ones and keeping what the schema does not describe', () => {
    const dialog = open({ schema: SCHEMA, props: { caption: 'Old', other: 1 }, inputBindings: { rows: 'x' } });

    dialog.setProp('caption', 'New');
    dialog.setProp('size', '12');
    dialog.setProp('bold', true);
    dialog.setProp('align', '');
    dialog.setBinding('rows', 'order.lineItems');
    dialog.onSave();

    expect(close).toHaveBeenCalledWith({ props: { caption: 'New', other: 1, size: 12, bold: true }, inputBindings: { rows: 'order.lineItems' } });
  });

  it('drops a binding that is cleared', () => {
    const dialog = open({ schema: SCHEMA, inputBindings: { rows: 'order.lineItems' } });

    dialog.setBinding('rows', '');
    dialog.onSave();

    expect(close).toHaveBeenCalledWith({ props: {}, inputBindings: {} });
  });

  it('edits structured props as JSON and refuses to save invalid JSON', () => {
    const dialog = open({ schema: SCHEMA, props: { items: [1] } });

    dialog.setJson('items', '[1, 2');
    expect(dialog.valid()).toBe(false);

    dialog.setJson('items', '[1, 2]');
    expect(dialog.valid()).toBe(true);
    dialog.onSave();
    expect(close).toHaveBeenCalledWith({ props: { items: [1, 2] }, inputBindings: {} });
  });

  it('removes a JSON prop that is emptied', () => {
    const dialog = open({ schema: SCHEMA, props: { items: [1] } });

    dialog.setJson('items', '  ');
    dialog.onSave();

    expect(close).toHaveBeenCalledWith({ props: {}, inputBindings: {} });
  });

  it('edits the props of an undescribed widget as JSON, so a save drops none of them', () => {
    const dialog = open({ props: { color: 'red', limit: 3 } });

    expect(dialog.fields.map((field) => [field.name, field.kind])).toEqual([
      ['color', 'json'],
      ['limit', 'json'],
    ]);
    dialog.onSave();
    expect(close).toHaveBeenCalledWith({ props: { color: 'red', limit: 3 }, inputBindings: {} });
  });

  it('says so when the widget has no props, and shows no bindings without ports', () => {
    open({ schema: { type: 'object', properties: {} }, inputPorts: [] });

    expect(control('widget-settings-no-props')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid^="widget-binding-"]')).toBeNull();
  });
});
