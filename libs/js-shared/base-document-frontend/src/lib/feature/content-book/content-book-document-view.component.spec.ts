import { HttpErrorResponse } from '@angular/common/http';
import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Document, DocumentBlock, DocumentStatus } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { DOCUMENT_ACCESS_CONTEXT } from '../../domain/document-access-context';
import { DocumentEditorComponent } from '../document-editor/document-editor.component';
import { DocumentReaderComponent } from '../document-sign/document-reader.component';
import { ContentBookDocumentViewComponent } from './content-book-document-view.component';

@Component({ selector: 'pp-document-reader', template: '<p data-testid="reader">{{ locale() }}:{{ blocks().length }}</p>' })
class ReaderStubComponent {
  readonly documentId = input.required<string>();
  readonly locale = input.required<string>();
  readonly blocks = input.required<DocumentBlock[]>();
}

@Component({ selector: 'pp-document-editor', template: '<p data-testid="editor">{{ locale() }}:{{ translationExists() }}</p>' })
class EditorStubComponent {
  readonly documentId = input.required<string>();
  readonly locale = input.required<string>();
  readonly blocks = input.required<DocumentBlock[]>();
  readonly translationExists = input(true);
  readonly inputPorts = input<unknown[]>([]);
}

const NOT_FOUND = new HttpErrorResponse({ status: 404 });
const translation = (locale: string, count = 1) => ({ locale, status: DocumentStatus.PUBLISHED, blocks: Array.from({ length: count }, (_, index) => ({ id: `b${index}` })) });
const page = (sourceLocale = 'en'): Document => new Document('d-1', 'org', 'overview', 'Overview', undefined, undefined, undefined, sourceLocale, false, [], ['editor']);

describe('ContentBookDocumentViewComponent', () => {
  let fixture: ComponentFixture<ContentBookDocumentViewComponent>;
  let getTranslation: ReturnType<typeof vi.fn>;
  let roles: ReturnType<typeof signal<readonly string[]>>;

  const byTestId = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);

  /** Answers published / draft reads per locale; a locale left out is the 404 that means "nothing here". */
  function answer(published: Record<string, number>, drafts: Record<string, number> = {}): void {
    getTranslation.mockImplementation((_id: string, locale: string, draft: boolean) => {
      const count = (draft ? drafts : published)[locale];
      return count === undefined ? Promise.reject(NOT_FOUND) : Promise.resolve(translation(locale, count));
    });
  }

  async function render(locale = 'de', document: Document | null = page()): Promise<void> {
    fixture = TestBed.createComponent(ContentBookDocumentViewComponent);
    fixture.componentRef.setInput('documentId', 'd-1');
    fixture.componentRef.setInput('locale', locale);
    fixture.componentRef.setInput('document', document ?? undefined);
    await settle();
  }

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
  }

  beforeEach(() => {
    getTranslation = vi.fn();
    roles = signal<readonly string[]>(['reader']);
    TestBed.configureTestingModule({
      providers: [provideTranslocoTesting({ translations: { en: {} } }), { provide: BaseDocumentService, useValue: { getTranslation } }, { provide: DOCUMENT_ACCESS_CONTEXT, useFactory: () => ({ roles }) }],
    }).overrideComponent(ContentBookDocumentViewComponent, {
      remove: { imports: [DocumentReaderComponent, DocumentEditorComponent] },
      add: { imports: [ReaderStubComponent, EditorStubComponent] },
    });
  });

  it('reads the published content in the reader’s language', async () => {
    answer({ de: 2, en: 1 });
    await render('de');

    expect(byTestId('reader')?.textContent).toBe('de:2');
    expect(getTranslation).toHaveBeenCalledWith('d-1', 'de', false);
  });

  it('falls back to the source language when the reader’s is not published', async () => {
    answer({ en: 3 });
    await render('de');

    expect(byTestId('reader')?.textContent).toBe('en:3');
  });

  it('tells a reader an unpublished page is not published, and never shows them the draft', async () => {
    answer({}, { de: 1 });
    await render('de');

    expect(byTestId('content-book-page-unpublished')).not.toBeNull();
    expect(getTranslation).not.toHaveBeenCalledWith('d-1', 'de', true);
    expect(byTestId('content-book-page-edit')).toBeNull();
  });

  it('shows an editor the draft of an unpublished page, under a banner', async () => {
    roles.set(['editor']);
    answer({}, { en: 4 });
    await render('de');

    expect(byTestId('content-book-page-draft')).not.toBeNull();
    expect(byTestId('reader')?.textContent).toBe('en:4');
  });

  it('swaps in the editor on the draft of the reader’s language, and back', async () => {
    roles.set(['editor']);
    answer({ de: 1 }, {});
    await render('de');

    byTestId('content-book-page-edit')?.click();
    await settle();
    expect(byTestId('editor')?.textContent).toBe('de:false');

    byTestId('content-book-page-edit')?.click();
    await settle();
    expect(byTestId('reader')?.textContent).toBe('de:1');
  });

  it('shows a failure other than a missing translation as an error', async () => {
    getTranslation.mockRejectedValue(new HttpErrorResponse({ status: 500, error: { errorText: 'Server fault' } }));
    await render('en');

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Server fault');
  });

  it('shows a page the reader may not see as restricted, without asking for it', async () => {
    await render('en', null);

    expect(byTestId('content-book-page-restricted')).not.toBeNull();
    expect(getTranslation).not.toHaveBeenCalled();
  });
});
