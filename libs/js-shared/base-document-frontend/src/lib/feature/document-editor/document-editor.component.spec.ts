import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { BlockKind, DocumentBlock, WidgetPlacement } from '../../domain/base-document';
import { DocumentEditorComponent } from './document-editor.component';

function widgetBlock(id: string, placement: WidgetPlacement): DocumentBlock {
  return { id, kind: BlockKind.WIDGET, placement, type: 'unregistered-widget' } as DocumentBlock;
}

/**
 * Hosts the editor the way BaseDocumentContainerComponent does — one instance, the document swapped
 * underneath it — because that is the case a one-shot `ngOnInit` seed would get wrong.
 */
@Component({
  standalone: true,
  imports: [DocumentEditorComponent],
  template: `<pp-document-editor [documentId]="documentId()" [locale]="locale()" [blocks]="blocks()" />`,
})
class EditorHostComponent {
  readonly documentId = input.required<string>();
  readonly locale = input<string>('en');
  readonly blocks = input.required<DocumentBlock[]>();
}

describe('DocumentEditorComponent', () => {
  let fixture: ComponentFixture<EditorHostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EditorHostComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // The add-block actions are labelled through TranslocoPipe, which pulls in TranslocoService.
        provideTranslocoTesting({ translations: { en: { 'base_document.document.content.add_text_block': 'Add text block', 'base_document.document.content.add_widget_block': 'Add widget' } } }),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { DOCUMENT_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
      ],
    });
    fixture = TestBed.createComponent(EditorHostComponent);
  });

  function editor(): DocumentEditorComponent {
    return fixture.debugElement.children[0].componentInstance;
  }

  function renderedBlockIds(): string[] {
    return (editor() as unknown as { standaloneBlocks(): DocumentBlock[] }).standaloneBlocks().map((b) => b.id);
  }

  /**
   * The seed has to land before the editor's own template is executed. Seeding from a `computed` threw
   * NG0600 (a reactive computation may not write signals) and seeding from an `effect()` would render
   * an empty list on this first pass, so asserting after a single `detectChanges` is the point.
   */
  it('renders the blocks it was handed on the first change-detection pass', () => {
    fixture.componentRef.setInput('documentId', 'getting-started');
    fixture.componentRef.setInput('blocks', [widgetBlock('block-1', WidgetPlacement.STANDALONE)]);
    fixture.detectChanges();

    expect(renderedBlockIds()).toEqual(['block-1']);
  });

  /** REFERENCED blocks only ever render inside a TEXT block's Tiptap content — see DocumentTextBlockComponent. */
  it('leaves referenced widget blocks out of the top-level list', () => {
    fixture.componentRef.setInput('documentId', 'getting-started');
    fixture.componentRef.setInput('blocks', [widgetBlock('standalone-1', WidgetPlacement.STANDALONE), widgetBlock('referenced-1', WidgetPlacement.REFERENCED)]);
    fixture.detectChanges();

    expect(renderedBlockIds()).toEqual(['standalone-1']);
  });

  /**
   * The one way a document with no content can get some. Without it the editor is a dead end for every
   * document whose locale has never been written, which is every newly created one — and an empty editor is
   * indistinguishable from a broken one.
   */
  it('offers to add a text block even when the document has none', async () => {
    fixture.componentRef.setInput('documentId', 'empty-doc');
    fixture.componentRef.setInput('blocks', []);
    fixture.detectChanges();

    const addButton = fixture.nativeElement.querySelector('[data-testid="document-add-text-block"]');
    expect(addButton).not.toBeNull();

    addButton.click();
    const request = TestBed.inject(HttpTestingController).expectOne('http://localhost:3000/organizations/processpuzzle-testbed/documents/empty-doc/translations/en/blocks');
    request.flush({ id: 'first', kind: 'TEXT' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(renderedBlockIds()).toEqual(['first']);
  });

  /**
   * A rejected append has to say what happened. The rejection is an `HttpErrorResponse`, which carries a
   * message but is not an `Error` — asserting on the status is what keeps the alert from going back to
   * reading `[object Object]`, which is what a bare `String(error)` produced.
   */
  it('reports why an append failed', async () => {
    fixture.componentRef.setInput('documentId', 'empty-doc');
    fixture.componentRef.setInput('blocks', []);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="document-add-text-block"]').click();
    const request = TestBed.inject(HttpTestingController).expectOne('http://localhost:3000/organizations/processpuzzle-testbed/documents/empty-doc/translations/en/blocks');
    request.flush({ error: 'nope' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.pp-document-editor__error').textContent).toContain('500');
  });

  /** The widget menu appears only where a WIDGET_REGISTRY has something to offer. */
  it('offers no widget menu without a widget registry', () => {
    fixture.componentRef.setInput('documentId', 'empty-doc');
    fixture.componentRef.setInput('blocks', []);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="document-add-widget-block"]')).toBeNull();
  });

  /** The container keeps one editor instance across documents, so the seed must re-run, not just run once. */
  it('re-seeds when another document is loaded into the same instance', () => {
    fixture.componentRef.setInput('documentId', 'first');
    fixture.componentRef.setInput('blocks', [widgetBlock('first-block', WidgetPlacement.STANDALONE)]);
    fixture.detectChanges();
    const firstInstance = editor();

    fixture.componentRef.setInput('documentId', 'second');
    fixture.componentRef.setInput('blocks', [widgetBlock('second-block', WidgetPlacement.STANDALONE)]);
    fixture.detectChanges();

    expect(editor()).toBe(firstInstance);
    expect(renderedBlockIds()).toEqual(['second-block']);
  });
});
