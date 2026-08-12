import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BaseDocumentStore } from '../domain/base-document.store';
import { BaseDocumentService } from '../domain/base-document.service';
import { Document, DocumentBlock } from '../domain/base-document';
import { DocumentEditorComponent } from './document-editor/document-editor.component';

/**
 * The Content tab's screen, mounted at `document/<id>/content` by `BASE_DOCUMENT_ROUTES` — a sibling of the
 * generic Details form rather than something stacked under it, which is what the `extraTabs` hook on
 * `BaseEntityDescriptor` exists for.
 *
 * Fetches the translation it edits instead of reading blocks off the entity in `BaseDocumentStore`: the
 * contract gives a document no root block list, and the store loads through `listDocuments`, whose
 * `DocumentSummary` carries no content at all. So the blocks shown here can only come from
 * `getDocumentTranslation`, and they are per locale.
 */
@Component({
  selector: 'pp-document-content-tab',
  standalone: true,
  imports: [DocumentEditorComponent, TranslocoPipe],
  template: `
    <div class="pp-document-content">
      @if (isLoading()) {
        <p>{{ 'base_document.document.content.loading' | transloco }}</p>
      } @else if (loadError(); as error) {
        <p role="alert">{{ error }}</p>
      } @else {
        <h2 class="pp-document-content__title">{{ title() }}</h2>
        <pp-document-editor [documentId]="entityId()" [locale]="locale()" [blocks]="blocks()" [translationExists]="translationExists()" />
      }
    </div>
  `,
  styles: [
    `
      /* The reading/writing surface, matching the status bar above it — same white, same corner radius. */
      .pp-document-content {
        background-color: #ffffff;
        border-radius: 6px;
        padding: 16px 20px 24px;
      }
      .pp-document-content__title {
        margin: 0 0 8px;
      }
      .pp-document-content > p {
        margin: 0;
      }
    `,
  ],
})
export class DocumentContentTabComponent implements OnInit {
  /**
   * Bound from the route's `:entityId` param by `withComponentInputBinding()`, the same way
   * `BaseEntityFormComponent` receives it — so a deep link and a reload resolve the same document as a
   * click through the tab does.
   */
  readonly entityId = input.required<string>();

  private readonly documentService = inject(BaseDocumentService);
  private readonly store = inject(BaseDocumentStore);
  private readonly transloco = inject(TranslocoService);

  /**
   * The locale whose draft is edited: the language the application is currently showing. A document is
   * translated per locale and the contract scopes every block operation to one, so *some* locale has to be
   * chosen here; following the UI language needs no extra control and is right for the common case of an
   * author writing in the language they are working in. A picker over `Document.translations` is the
   * natural next step, at which point this becomes its default rather than the only answer.
   */
  protected readonly locale = signal(this.transloco.getActiveLang());
  protected readonly blocks = signal<DocumentBlock[]>([]);
  protected readonly isLoading = signal(true);
  protected readonly loadError = signal<string | undefined>(undefined);
  /** False when the fetch 404s: the locale has no draft yet, and the first append has to create one. */
  protected readonly translationExists = signal(true);

  protected readonly title = computed(() => (this.store.currentEntity() as Document | undefined)?.title ?? '');

  ngOnInit(): void {
    // Also sets the current entity, so the tab bar's Details link stays enabled and the status bar keeps
    // naming the record — arriving here directly, nothing else has selected it.
    this.store.setCurrentEntity(this.entityId());
    void this.loadTranslation();
  }

  private async loadTranslation(): Promise<void> {
    try {
      const translation = await this.documentService.getTranslation(this.entityId(), this.locale());
      this.blocks.set(translation.blocks ?? []);
      this.translationExists.set(true);
    } catch (error) {
      // A 404 is the documented answer for "this locale has never been translated" — an empty editor with a
      // working Add button, not an error. Anything else (403 on a document the user may not read, a server
      // fault) is a real failure and is shown as one.
      if (error instanceof HttpErrorResponse && error.status === 404) {
        this.blocks.set([]);
        this.translationExists.set(false);
      } else {
        this.loadError.set(error instanceof Error ? error.message : String(error));
      }
    } finally {
      this.isLoading.set(false);
    }
  }
}
