import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { describe, expect, it } from 'vitest';
import { BlockKind, WidgetPlacement } from '../../domain/base-document';
import { DocumentDataContext } from '../report/document-data-context';
import { DocumentContentStore } from './document-content.store';

const SERVICE_ROOT = 'http://localhost:3000/organizations/processpuzzle-testbed';

function setUp(context?: Partial<DocumentDataContext>) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: SERVICE_ROOT } } },
      DocumentContentStore,
      ...(context ? [{ provide: DocumentDataContext, useValue: context }] : []),
    ],
  });
  return TestBed.inject(DocumentContentStore);
}

describe('DocumentContentStore, rendering with data', () => {
  it('resolves bindings through the data context of whoever renders the document', () => {
    const store = setUp({ resolve: (path: string) => (path === 'order.total' ? 42 : undefined) });

    expect(store.resolveBinding('order.total')).toBe(42);
    expect(store.resolveBinding('order.other')).toBeUndefined();
  });

  it('knows the locale it was initialized for', () => {
    const store = setUp();
    expect(store.currentLocale()).toBeNull();

    store.initialize('invoice', 'de', []);

    expect(store.currentLocale()).toBe('de');
  });

  it('saves a widget block’s props and bindings together', async () => {
    const store = setUp();
    store.initialize('invoice', 'en', [{ id: 'lines', kind: BlockKind.WIDGET, placement: WidgetPlacement.STANDALONE, type: 'entity-table', props: {} }]);

    const saving = store.saveWidgetBlock('lines', { props: { columns: [{ path: 'qty' }] }, inputBindings: { rows: 'order.lineItems' } });

    expect(store.blocks()[0].inputBindings).toEqual({ rows: 'order.lineItems' });
    const request = TestBed.inject(HttpTestingController).expectOne(`${SERVICE_ROOT}/documents/invoice/translations/en/blocks/lines`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toMatchObject({ props: { columns: [{ path: 'qty' }] }, inputBindings: { rows: 'order.lineItems' } });
    request.flush({ id: 'lines', kind: 'WIDGET', placement: 'STANDALONE', type: 'entity-table', props: { columns: [{ path: 'qty' }] }, inputBindings: { rows: 'order.lineItems' } });
    await saving;
  });
});
