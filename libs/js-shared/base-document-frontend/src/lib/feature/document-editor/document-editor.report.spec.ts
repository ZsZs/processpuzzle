import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { By } from '@angular/platform-browser';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BlockKind, DocumentBlock, DocumentInputPort, PortType, WidgetPlacement } from '../../domain/base-document';
import { DocumentPathCatalog } from '../report/document-path-catalog';
import { DocumentContentStore } from './document-content.store';
import { DocumentEditorComponent } from './document-editor.component';
import { DocumentTextBlockComponent } from './document-text-block.component';

const ORDER_PORT = Object.assign(new DocumentInputPort('order', PortType.ENTITY_REF), { entityType: 'Order' });
const TABLE: DocumentBlock = {
  id: 'lines',
  kind: BlockKind.WIDGET,
  placement: WidgetPlacement.STANDALONE,
  type: 'entity-table',
  props: { caption: 'Old', columns: [{ path: 'qty' }] },
  inputBindings: { rows: 'order.lineItems' },
};
const TEXT: DocumentBlock = { id: 'intro', kind: BlockKind.TEXT, editable: true, content: { type: 'doc', content: [{ type: 'paragraph' }] } };
const PATHS = {
  fields: [{ path: 'order.total', label: 'Total', format: 'number' as const }],
  collections: [{ path: 'order.lineItems', label: 'Line items', rowFields: [] }],
};

@Component({
  standalone: true,
  imports: [DocumentEditorComponent],
  template: `<pp-document-editor documentId="invoice" locale="en" [blocks]="blocks()" [inputPorts]="inputPorts()" />`,
})
class EditorHostComponent {
  readonly blocks = input<DocumentBlock[]>([]);
  readonly inputPorts = input<readonly DocumentInputPort[]>([]);
}

describe('DocumentEditorComponent, authoring a report', () => {
  let fixture: ComponentFixture<EditorHostComponent>;
  let dialogResult: unknown;
  const open = vi.fn();

  beforeEach(() => {
    dialogResult = undefined;
    open.mockReset().mockImplementation(() => ({ afterClosed: () => of(dialogResult) }));
    TestBed.configureTestingModule({
      imports: [EditorHostComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslocoTesting({ translations: {} }),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: 'http://localhost:3000/org' } } },
        { provide: MatDialog, useValue: { open } },
        { provide: DocumentPathCatalog, useValue: { describe: vi.fn().mockResolvedValue(PATHS) } },
      ],
    });
    fixture = TestBed.createComponent(EditorHostComponent);
  });

  function store(): DocumentContentStore {
    return fixture.debugElement.query(By.directive(DocumentEditorComponent)).injector.get(DocumentContentStore);
  }

  function editor(): DocumentEditorComponent {
    return fixture.debugElement.query(By.directive(DocumentEditorComponent)).componentInstance;
  }

  it('hands the ports to the store without re-seeding the blocks when only they change', () => {
    fixture.componentRef.setInput('blocks', [TABLE]);
    fixture.detectChanges();
    store().initialize('invoice', 'en', []);

    fixture.componentRef.setInput('inputPorts', [ORDER_PORT]);
    fixture.detectChanges();

    expect(store().inputPorts()).toEqual([ORDER_PORT]);
    expect(store().blocks()).toEqual([]);
  });

  it('offers table settings on an entity-table block and saves what the dialog returns', async () => {
    fixture.componentRef.setInput('blocks', [TABLE]);
    fixture.componentRef.setInput('inputPorts', [ORDER_PORT]);
    fixture.detectChanges();
    const save = vi.spyOn(store(), 'saveWidgetBlock').mockResolvedValue();
    dialogResult = { source: 'order.otherItems', columns: [{ path: 'name' }], totals: [] };

    fixture.debugElement.query(By.css('[data-testid="document-table-settings-lines"]')).nativeElement.click();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(open.mock.calls[0][1].data).toEqual({
      collections: PATHS.collections,
      current: { source: 'order.lineItems', columns: [{ path: 'qty' }], totals: [], caption: 'Old' },
    });
    expect(save).toHaveBeenCalledWith('lines', {
      props: { caption: 'Old', columns: [{ path: 'name' }], totals: [] },
      inputBindings: { rows: 'order.otherItems' },
    });
  });

  it('saves nothing when the dialog is dismissed, and keeps a new caption', async () => {
    fixture.componentRef.setInput('blocks', [{ ...TABLE, props: undefined, inputBindings: undefined }]);
    fixture.detectChanges();
    const save = vi.spyOn(store(), 'saveWidgetBlock').mockResolvedValue();

    await editor().onConfigureTable(store().blocks()[0]);
    expect(save).not.toHaveBeenCalled();

    dialogResult = { source: 'order.lineItems', columns: [], totals: [], caption: 'New' };
    await editor().onConfigureTable(store().blocks()[0]);
    expect(save).toHaveBeenCalledWith('lines', { props: { columns: [], totals: [], caption: 'New' }, inputBindings: { rows: 'order.lineItems' } });
  });

  it('offers settings on any other widget block, from the widget schema and the document paths', async () => {
    const note: DocumentBlock = { id: 'note', kind: BlockKind.WIDGET, placement: WidgetPlacement.STANDALONE, type: 'unregistered-widget', props: { color: 'red' }, inputBindings: { value: 'order.total' } };
    fixture.componentRef.setInput('blocks', [note]);
    fixture.componentRef.setInput('inputPorts', [ORDER_PORT]);
    fixture.detectChanges();
    const save = vi.spyOn(store(), 'saveWidgetBlock').mockResolvedValue();
    dialogResult = { props: { color: 'blue' }, inputBindings: { value: 'order.lineItems' } };

    fixture.debugElement.query(By.css('[data-testid="document-widget-settings-note"]')).nativeElement.click();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(open.mock.calls[0][1].data).toEqual({
      name: 'unregistered-widget',
      schema: undefined,
      inputPorts: [],
      props: { color: 'red' },
      inputBindings: { value: 'order.total' },
      paths: ['order.lineItems', 'order.total'],
    });
    expect(save).toHaveBeenCalledWith('note', { props: { color: 'blue' }, inputBindings: { value: 'order.lineItems' } });
  });

  it('saves nothing when the widget settings are dismissed', async () => {
    const bare: DocumentBlock = { id: 'bare', kind: BlockKind.WIDGET, placement: WidgetPlacement.STANDALONE };
    fixture.componentRef.setInput('blocks', [bare]);
    fixture.detectChanges();
    const save = vi.spyOn(store(), 'saveWidgetBlock').mockResolvedValue();

    await editor().onConfigureWidget(store().blocks()[0]);

    expect(open.mock.calls[0][1].data).toMatchObject({ name: '', props: {}, inputBindings: {} });
    expect(save).not.toHaveBeenCalled();
  });

  it('inserts a data field at the caret of a text block', async () => {
    fixture.componentRef.setInput('blocks', [TEXT]);
    fixture.componentRef.setInput('inputPorts', [ORDER_PORT]);
    fixture.detectChanges();
    const textBlock = fixture.debugElement.query(By.directive(DocumentTextBlockComponent)).componentInstance as DocumentTextBlockComponent;
    const textBlockApi = textBlock as unknown as { hasPorts(): boolean; onInsertField(): Promise<void>; editorInstance(): { getJSON(): { content: { content?: unknown[] }[] } } };
    vi.spyOn(store(), 'saveTextBlockContent').mockImplementation(() => undefined);

    expect(textBlockApi.hasPorts()).toBe(true);
    await textBlockApi.onInsertField();
    expect(open.mock.calls[0][1].data).toEqual({ fields: PATHS.fields });
    expect(textBlockApi.editorInstance().getJSON().content[0].content).toBeUndefined();

    dialogResult = { path: 'order.total', format: 'number' };
    await textBlockApi.onInsertField();
    expect(textBlockApi.editorInstance().getJSON().content[0].content).toEqual([{ type: 'dataField', attrs: { path: 'order.total', format: 'number' } }]);
  });

  it('shows the insert-field button only while editing a document with ports', () => {
    fixture.componentRef.setInput('blocks', [TEXT]);
    fixture.componentRef.setInput('inputPorts', [ORDER_PORT]);
    fixture.detectChanges();
    const textBlock = fixture.debugElement.query(By.directive(DocumentTextBlockComponent)).componentInstance as unknown as { isEditing: { set(value: boolean): void } };

    expect(fixture.nativeElement.querySelector('[data-testid="document-text-block-insert-field"]')).toBeNull();
    textBlock.isEditing.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="document-text-block-insert-field"]')).not.toBeNull();
  });
});
