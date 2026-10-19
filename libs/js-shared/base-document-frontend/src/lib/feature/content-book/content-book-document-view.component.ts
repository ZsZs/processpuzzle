import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { Document, DocumentBlock } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { DOCUMENT_ACCESS_CONTEXT, mayEdit } from '../../domain/document-access-context';
import { DocumentEditorComponent } from '../document-editor/document-editor.component';
import { DocumentReaderComponent } from '../document-sign/document-reader.component';
import { DocumentDataContext } from '../report/document-data-context';

type PageState =
  | { kind: 'loading' }
  | { kind: 'published'; locale: string; blocks: DocumentBlock[] }
  | { kind: 'draft'; locale: string; blocks: DocumentBlock[] }
  | { kind: 'unpublished' }
  | { kind: 'error'; message: string };

/**
 * One page of a content book: the document's published content in the reader's language, falling back to the
 * document's source language — and, when the book's toolbar asks for it ({@link toggleEditing}), the content
 * editor on the draft. Editing a published page edits its draft: readers keep the published revision until the
 * next publish.
 *
 * Reading never shows a draft to someone who may not edit it: a page with nothing published reads "not published
 * yet". An editor is shown the draft instead, under a banner, because a book is where they assemble pages before
 * publishing them. An archived page is never edited.
 *
 * Provides the {@link DocumentDataContext} its widgets resolve their port bindings through, bound to the ports'
 * defaults: a book hands a page no values of its own, so a collection port lists what its `defaultRsqlFilter`
 * selects.
 */
@Component({
  selector: 'pp-content-book-document-view',
  imports: [DocumentReaderComponent, DocumentEditorComponent, TranslocoPipe],
  providers: [DocumentDataContext],
  template: `
    @if (!document()) {
      <p class="pp-content-book-page__message" data-testid="content-book-page-restricted">{{ 'base_document.content_book.widget.restricted_text' | transloco }}</p>
    } @else {
      @if (editing()) {
        @if (draft(); as draft) {
          <p class="pp-content-book-page__note">{{ 'base_document.content_book.widget.edit_note' | transloco }}</p>
          <pp-document-editor [documentId]="documentId()" [locale]="draft.locale" [blocks]="draft.blocks" [translationExists]="draft.exists" [inputPorts]="document()?.inputPorts ?? []" />
        } @else {
          <p class="pp-content-book-page__message">{{ 'base_document.content_book.widget.page_loading' | transloco }}</p>
        }
      } @else {
        @switch (page().kind) {
          @case ('loading') {
            <p class="pp-content-book-page__message">{{ 'base_document.content_book.widget.page_loading' | transloco }}</p>
          }
          @case ('unpublished') {
            <p class="pp-content-book-page__message" data-testid="content-book-page-unpublished">{{ 'base_document.content_book.widget.not_published' | transloco }}</p>
          }
          @case ('error') {
            <p class="pp-content-book-page__message" role="alert">{{ errorMessage() }}</p>
          }
          @default {
            @if (page().kind === 'draft') {
              <p class="pp-content-book-page__banner" data-testid="content-book-page-draft">{{ 'base_document.content_book.widget.draft_banner' | transloco }}</p>
            }
            <pp-document-reader [documentId]="documentId()" [locale]="pageLocale()" [blocks]="pageBlocks()" />
          }
        }
      }
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .pp-content-book-page__banner {
      background: color-mix(in srgb, var(--mat-sys-tertiary, #b58100) 15%, transparent);
      border-radius: 4px;
      margin: 0 0 12px;
      padding: 8px 12px;
    }
    .pp-content-book-page__note,
    .pp-content-book-page__message {
      margin: 0 0 12px;
      opacity: 0.8;
    }
  `,
})
export class ContentBookDocumentViewComponent {
  readonly documentId = input.required<string>();
  readonly locale = input.required<string>();
  /** The document, from the book's listing. Absent when the reader may not see it: the book shows the page, not its content. */
  readonly document = input<Document | undefined>(undefined);

  private readonly documentService = inject(BaseDocumentService);
  private readonly accessContext = inject(DOCUMENT_ACCESS_CONTEXT, { optional: true });

  private readonly data = inject(DocumentDataContext);

  /** Whether the reader may edit this page: an editor of the document, and the document not archived. */
  readonly canEdit = computed(() => {
    const document = this.document();
    return document !== undefined && !document.archive && mayEdit(this.accessContext, document.editorRoles);
  });
  private readonly editingState = signal(false);
  readonly editing = this.editingState.asReadonly();
  protected readonly page = signal<PageState>({ kind: 'loading' });
  protected readonly draft = signal<{ locale: string; blocks: DocumentBlock[]; exists: boolean } | undefined>(undefined);

  protected readonly pageLocale = computed(() => {
    const page = this.page();
    return page.kind === 'published' || page.kind === 'draft' ? page.locale : this.locale();
  });
  protected readonly pageBlocks = computed(() => {
    const page = this.page();
    return page.kind === 'published' || page.kind === 'draft' ? page.blocks : [];
  });
  protected readonly errorMessage = computed(() => {
    const page = this.page();
    return page.kind === 'error' ? page.message : '';
  });

  constructor() {
    // A new page, or the same page in another language, starts in reading mode.
    effect(() => {
      const documentId = this.documentId();
      const locale = this.locale();
      const document = this.document();
      untracked(() => {
        this.editingState.set(false);
        this.draft.set(undefined);
        if (document) void this.loadPage(documentId, locale, document);
      });
    });
    effect(() => {
      const ports = this.document()?.inputPorts ?? [];
      untracked(() => void this.data.bind(ports, {}));
    });
  }

  /** Swaps between the published reader and the draft editor; a page that may not be edited stays as it is. */
  toggleEditing(): void {
    const document = this.document();
    if (!document) return;

    if (this.editing()) {
      this.editingState.set(false);
      void this.loadPage(this.documentId(), this.locale(), document);
    } else if (this.canEdit()) {
      this.editingState.set(true);
      void this.loadDraft(this.documentId(), this.locale());
    }
  }

  /** Bumped by every load, so a slow answer for a page the reader has already left cannot overwrite the current one. */
  private loadSequence = 0;

  private async loadPage(documentId: string, locale: string, document: Document): Promise<void> {
    const sequence = ++this.loadSequence;
    const settle = (state: PageState): void => {
      if (sequence === this.loadSequence) this.page.set(state);
    };
    this.page.set({ kind: 'loading' });
    const locales = locale === document.sourceLocale ? [locale] : [locale, document.sourceLocale];
    try {
      for (const candidate of locales) {
        const published = await this.translationOrUndefined(documentId, candidate, false);
        if (published) return settle({ kind: 'published', locale: candidate, blocks: published.blocks ?? [] });
      }
      if (!this.canEdit()) return settle({ kind: 'unpublished' });

      for (const candidate of locales) {
        const draft = await this.translationOrUndefined(documentId, candidate, true);
        if (draft) return settle({ kind: 'draft', locale: candidate, blocks: draft.blocks ?? [] });
      }
      settle({ kind: 'unpublished' });
    } catch (error) {
      settle({ kind: 'error', message: messageOf(error) });
    }
  }

  private async loadDraft(documentId: string, locale: string): Promise<void> {
    try {
      const draft = await this.translationOrUndefined(documentId, locale, true);
      this.draft.set({ locale, blocks: draft?.blocks ?? [], exists: draft !== undefined });
    } catch (error) {
      this.editingState.set(false);
      this.page.set({ kind: 'error', message: messageOf(error) });
    }
  }

  /** The translation, or `undefined` for the 404 that means "nothing in this locale" — published, or at all. */
  private async translationOrUndefined(documentId: string, locale: string, draft: boolean) {
    try {
      return await this.documentService.getTranslation(documentId, locale, draft);
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404) return undefined;
      throw error;
    }
  }
}

function messageOf(error: unknown): string {
  if (error instanceof HttpErrorResponse) return error.error?.errorText ?? error.message;
  return error instanceof Error ? error.message : String(error);
}
