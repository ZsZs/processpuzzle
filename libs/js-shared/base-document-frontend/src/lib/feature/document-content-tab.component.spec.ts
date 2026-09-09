import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { DocumentContentTabComponent } from './document-content-tab.component';
import { DocumentEditorComponent } from './document-editor/document-editor.component';

const SERVICE_ROOT = 'http://localhost:3000/organizations/processpuzzle-testbed';

describe('DocumentContentTabComponent', () => {
  let fixture: ComponentFixture<DocumentContentTabComponent>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // Flat dotted keys: TestTranslocoLoader drops nested objects when no scope is configured.
        provideTranslocoTesting({ translations: { en: { 'base_document.document.content.loading': 'Loading content…', 'base_document.document.content.add_text_block': 'Add text block' } } }),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { DOCUMENT_SERVICE_ROOT: SERVICE_ROOT } } },
      ],
    });
    fixture = TestBed.createComponent(DocumentContentTabComponent);
    controller = TestBed.inject(HttpTestingController);
  });

  /** The store issues its own `load({})` on init; the content tab's own request is the translation GET. */
  function expectTranslationRequest(documentId = 'q3-plan', locale = 'en') {
    return controller.expectOne((candidate) => candidate.url === `${SERVICE_ROOT}/documents/${documentId}/translations/${locale}`);
  }

  async function render(documentId = 'q3-plan') {
    fixture.componentRef.setInput('entityId', documentId);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  /** The fire-and-forget initial fetch settles on the following event-loop turn. */
  async function settleAndRender() {
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
  }

  function editor(): DocumentEditorComponent | null {
    return fixture.debugElement.query(By.directive(DocumentEditorComponent))?.componentInstance ?? null;
  }

  /**
   * The blocks can only come from this call. `listDocuments` returns `DocumentSummary`, which the contract
   * defines as the metadata "minus block content", and the generic store loads through exactly that — so
   * reading blocks off the entity in the store would always show an empty document.
   */
  it('fetches the locale draft translation and feeds its blocks to the editor', async () => {
    await render();

    const request = expectTranslationRequest();
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('draft')).toBe('true');
    request.flush({ locale: 'en', status: 'DRAFT', blocks: [{ id: 'intro', kind: 'TEXT', content: { type: 'doc', content: [] } }] });
    await settleAndRender();

    expect(editor()?.blocks()).toHaveLength(1);
    expect(editor()?.locale()).toBe('en');
    expect(editor()?.documentId()).toBe('q3-plan');
  });

  /**
   * 404 is the contract's documented answer for a locale that has never been translated. That is an empty
   * editor with a working Add button, not a failure — the first append creates the draft.
   */
  it('treats a missing translation as an empty draft rather than an error', async () => {
    await render();

    expectTranslationRequest().flush({ message: 'no translation' }, { status: 404, statusText: 'Not Found' });
    await settleAndRender();

    expect(editor()).not.toBeNull();
    expect(editor()?.blocks()).toEqual([]);
    expect(editor()?.translationExists()).toBe(false);
  });

  /** A 403 on a document the user may not read is a real failure, and is not silently shown as empty content. */
  it('surfaces any other failure instead of rendering an empty editor', async () => {
    await render();

    expectTranslationRequest().flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    await settleAndRender();

    expect(editor()).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});
