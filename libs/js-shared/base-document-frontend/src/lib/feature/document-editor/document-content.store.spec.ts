import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { BlockKind, WidgetPlacement } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { DocumentContentService } from './document-content.service';
import { DocumentContentStore } from './document-content.store';

const SERVICE_ROOT = 'http://localhost:3000/organizations/processpuzzle-testbed';
const BLOCKS_URL = `${SERVICE_ROOT}/documents/q3-plan/translations/hu/blocks`;

/**
 * Lets an `await` continuation inside the store run. Needed only where one call waits on another — creating
 * the draft before the first block — since the append itself issues its request in the calling tick.
 */
const settle = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

describe('DocumentContentStore', () => {
  let store: DocumentContentStore;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { DOCUMENT_SERVICE_ROOT: SERVICE_ROOT } } },
        DocumentContentStore,
      ],
    });
    store = TestBed.inject(DocumentContentStore);
    controller = TestBed.inject(HttpTestingController);
    store.initialize('q3-plan', 'hu', []);
  });

  /**
   * Blocks belong to a locale's draft, not to the document: the contract scopes every block operation to
   * `translations/{locale}`, and the earlier document-level URLs 404'd against the real backend.
   */
  it('appends a text block to the locale draft', async () => {
    const pending = store.appendTextBlock();

    const request = controller.expectOne(BLOCKS_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.kind).toBe(BlockKind.TEXT);
    // A valid empty ProseMirror document, because DocumentTextBlockComponent hands it straight to Editor.
    expect(request.request.body.content).toEqual({ type: 'doc', content: [{ type: 'paragraph' }] });
    request.flush({ id: 'intro', kind: 'TEXT' });

    expect(await pending).toBe('intro');
    expect(store.blocks()).toHaveLength(1);
  });

  it('appends a standalone widget in document order', async () => {
    void store.appendStandaloneWidget('entity-grid', { entity: 'Order' });

    const request = controller.expectOne(BLOCKS_URL);
    expect(request.request.body.placement).toBe(WidgetPlacement.STANDALONE);
    expect(request.request.body.type).toBe('entity-grid');
    request.flush({ id: 'grid-1', kind: 'WIDGET', placement: 'STANDALONE' });
  });

  it('keeps embedded widgets REFERENCED, so they render inside prose rather than beside it', async () => {
    void store.appendReferencedWidget('entity-grid');

    const request = controller.expectOne(BLOCKS_URL);
    expect(request.request.body.placement).toBe(WidgetPlacement.REFERENCED);
    request.flush({ id: 'grid-1', kind: 'WIDGET', placement: 'REFERENCED' });
  });

  it('addresses one block by id on replace and delete', async () => {
    store.initialize('q3-plan', 'hu', [{ id: 'intro', kind: BlockKind.TEXT }]);

    void store.saveWidgetBlockProps('intro', { title: 'x' });
    const put = controller.expectOne(`${BLOCKS_URL}/intro`);
    expect(put.request.method).toBe('PUT');
    // The id travels in the URL, not the body — the contract's block input has no id field.
    expect(put.request.body).not.toHaveProperty('id');
    put.flush({ id: 'intro', kind: 'TEXT', props: { title: 'x' } });

    void store.deleteBlock('intro');
    expect(controller.expectOne(`${BLOCKS_URL}/intro`).request.method).toBe('DELETE');
  });

  it('reorders through the locale draft as a group', () => {
    void store.reorder(['b', 'a']);

    const request = controller.expectOne(`${BLOCKS_URL}/reorder`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ blockIds: ['b', 'a'] });
    request.flush([]);
  });

  /**
   * Appending to a locale the document has never been translated into is a 404, so "add a text block" on a
   * fresh document would fail with nothing the user could do about it. The draft is created first, blank —
   * omitting blocks would make the server copy another locale's prose, which is not what the click meant.
   */
  it('creates the draft before the first append when the locale has none', async () => {
    store.initialize('q3-plan', 'hu', [], false);

    const pending = store.appendTextBlock();

    const created = controller.expectOne(`${SERVICE_ROOT}/documents/q3-plan/translations`);
    expect(created.request.method).toBe('POST');
    expect(created.request.body).toEqual({ locale: 'hu', blocks: [] });
    created.flush({ locale: 'hu', status: 'DRAFT', blocks: [] });
    await settle();

    controller.expectOne(BLOCKS_URL).flush({ id: 'intro', kind: 'TEXT' });
    expect(await pending).toBe('intro');
  });

  it('creates the draft once, not on every append', async () => {
    store.initialize('q3-plan', 'hu', [], false);

    const first = store.appendTextBlock();
    controller.expectOne(`${SERVICE_ROOT}/documents/q3-plan/translations`).flush({ locale: 'hu', status: 'DRAFT', blocks: [] });
    await settle();
    controller.expectOne(BLOCKS_URL).flush({ id: 'one', kind: 'TEXT' });
    await first;

    const second = store.appendTextBlock();
    controller.expectNone(`${SERVICE_ROOT}/documents/q3-plan/translations`);
    controller.expectOne(BLOCKS_URL).flush({ id: 'two', kind: 'TEXT' });
    await second;

    expect(store.blocks().map((block) => block.id)).toEqual(['one', 'two']);
  });

  it('refuses to touch anything before it knows which document and locale', async () => {
    const uninitialized = new DocumentContentStore(TestBed.inject(DocumentContentService), TestBed.inject(BaseDocumentService));

    await expect(uninitialized.appendTextBlock()).rejects.toThrow(/initialize/);
  });
});
