import { computed, Injectable, Signal, signal, WritableSignal } from '@angular/core';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { BlockKind, DocumentBlock, WidgetPlacement } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { DocumentContentService } from './document-content.service';

const TEXT_AUTOSAVE_DEBOUNCE_MS = 800;

/** Smallest document Tiptap accepts: one empty paragraph. */
const EMPTY_TIPTAP_DOCUMENT: Record<string, unknown> = { type: 'doc', content: [{ type: 'paragraph' }] };

/**
 * Owns block-level editing state for one open document, entirely separately from
 * BaseDocumentStore/BaseEntityStore — see the earlier discussion of why: continuous,
 * granular saves through the block endpoints must never be able to collide with the
 * Properties form's own save, which is why that form now hits updateDocumentProperties
 * instead of the whole-document PUT.
 *
 * Not a signalStore/NgRx composition on purpose — there's no list/form/tabs shape to
 * compose with here, just one array of blocks and some debounced side effects. A plain
 * injectable with signals is the honest fit.
 */
@Injectable()
export class DocumentContentStore {
  private readonly blocksSignal: WritableSignal<DocumentBlock[]> = signal([]);
  private documentId: string | null = null;
  /** The locale whose draft this session edits. Every block call is scoped to it — see DocumentContentService. */
  private locale: string | null = null;
  /** False until a draft is known to exist for {@link locale}; see {@link ensureTranslationExists}. */
  private translationExists = false;

  private readonly textSaveSubjects = new Map<string, Subject<Record<string, unknown>>>();

  constructor(
    private readonly contentService: DocumentContentService,
    private readonly documentService: BaseDocumentService,
  ) {}

  readonly blocks: Signal<DocumentBlock[]> = this.blocksSignal.asReadonly();

  readonly blocksById: Signal<ReadonlyMap<string, DocumentBlock>> = computed(() => {
    const map = new Map<string, DocumentBlock>();
    for (const block of this.blocksSignal()) {
      map.set(block.id, block);
    }
    return map;
  });

  /**
   * Called whenever the host component loads a document — see DocumentEditorComponent.
   *
   * `translationExists` says whether the locale already has a draft on the server. The content tab knows:
   * it just fetched the translation, or got a 404 for it. Passing it in rather than probing again keeps the
   * first append from a needless round trip, and makes the "locale has no draft yet" case explicit instead
   * of something inferred from an empty block list — a published locale can legitimately have zero blocks.
   */
  initialize(documentId: string, locale: string, blocks: DocumentBlock[], translationExists = true) {
    this.documentId = documentId;
    this.locale = locale;
    this.translationExists = translationExists;
    this.blocksSignal.set(blocks);
  }

  /**
   * Host-context port resolution is deliberately out of scope here — base-document-api.yaml is
   * explicit that resolving a bound value is entirely a frontend runtime concern, and for an
   * document specifically that means whatever embeds it (an app-shell page, another document's
   * widget block) is responsible for supplying it. This placeholder returns undefined for every
   * port until that host-context wiring exists; it's the one seam DocumentEditorComponent's
   * caller is expected to override once base-app's embedding story is designed.
   */
  resolveBinding(portName: string): unknown {
    return undefined;
  }

  /**
   * Debounced per block id so a fast typist doesn't fire a PUT per keystroke, but two different
   * blocks being edited near-simultaneously (unlikely today, single-editor-per-document, but the
   * per-id Subject costs nothing) still save independently rather than one debounce window
   * dropping the other's edit.
   */
  saveTextBlockContent(blockId: string, content: Record<string, unknown>) {
    // Reflect the edit locally immediately — the debounce only delays the network write, not
    // what the rest of the editor (e.g. a live word count) sees.
    this.patchLocalBlock(blockId, { content });

    let subject = this.textSaveSubjects.get(blockId);
    if (!subject) {
      subject = new Subject<Record<string, unknown>>();
      subject.pipe(debounceTime(TEXT_AUTOSAVE_DEBOUNCE_MS)).subscribe((latestContent) => {
        void this.persistBlock(blockId, { content: latestContent });
      });
      this.textSaveSubjects.set(blockId, subject);
    }
    subject.next(content);
  }

  /** Widget prop edits save immediately — there's no keystroke-rate concern the way there is for Tiptap content. */
  async saveWidgetBlockProps(blockId: string, props: Record<string, unknown>) {
    this.patchLocalBlock(blockId, { props });
    await this.persistBlock(blockId, { props });
  }

  /**
   * A new, empty TEXT block at the end of the document — the one way a document with no content can get
   * some. An empty Tiptap doc rather than no `content` at all, because DocumentTextBlockComponent hands the
   * value straight to `Editor`, which requires a valid ProseMirror document.
   */
  async appendTextBlock(): Promise<string> {
    return this.appendBlock({ kind: BlockKind.TEXT, editable: true, content: EMPTY_TIPTAP_DOCUMENT });
  }

  /** A widget rendered as a block of its own, in document order — as opposed to {@link appendReferencedWidget}. */
  async appendStandaloneWidget(type: string, props: Record<string, unknown> = {}): Promise<string> {
    return this.appendBlock({ kind: BlockKind.WIDGET, placement: WidgetPlacement.STANDALONE, type, props });
  }

  /**
   * Inserts a new REFERENCED widget block and returns its server-assigned id, ready for the
   * caller to embed via a widgetEmbed node (see DocumentEditorComponent's insert-widget command).
   */
  async appendReferencedWidget(type: string, props: Record<string, unknown> = {}): Promise<string> {
    return this.appendBlock({ kind: BlockKind.WIDGET, placement: WidgetPlacement.REFERENCED, type, props });
  }

  async deleteBlock(blockId: string): Promise<void> {
    const { documentId, locale } = this.requireTarget();
    // Left to the caller: a 409 here means the widget is still referenced elsewhere (see
    // DocumentBlockReferencedException) and the editor should surface that, not swallow it.
    await this.contentService.deleteBlock(documentId, locale, blockId);
    this.blocksSignal.update((blocks) => blocks.filter((b) => b.id !== blockId));
  }

  async reorder(blockIds: string[]): Promise<void> {
    const { documentId, locale } = this.requireTarget();
    const reordered = await this.contentService.reorderBlocks(documentId, locale, blockIds);
    this.blocksSignal.set(reordered);
  }

  private async appendBlock(block: Omit<DocumentBlock, 'id'>): Promise<string> {
    const { documentId, locale } = this.requireTarget();
    // Guarded rather than delegated to an always-awaited helper, so the common case — a locale that already
    // has a draft — reaches the POST in this same tick instead of one microtask later.
    if (!this.translationExists) await this.createDraftTranslation(documentId, locale);
    const created = await this.contentService.appendBlock(documentId, locale, block);
    this.blocksSignal.update((blocks) => [...blocks, created]);
    return created.id;
  }

  /**
   * Creates the locale's draft before the first block is appended to it. Appending to a locale the document
   * has never been translated into is a 404, and "add a text block" on a fresh document would otherwise fail
   * with nothing the user could do about it. Deliberately with an explicit empty block list, so the new
   * draft starts blank rather than inheriting the source locale's prose.
   */
  private async createDraftTranslation(documentId: string, locale: string): Promise<void> {
    await this.documentService.addTranslation(documentId, locale, []);
    this.translationExists = true;
  }

  private requireTarget(): { documentId: string; locale: string } {
    if (!this.documentId || !this.locale) throw new Error('DocumentContentStore.initialize() was not called');
    return { documentId: this.documentId, locale: this.locale };
  }

  private patchLocalBlock(blockId: string, patch: Partial<DocumentBlock>) {
    this.blocksSignal.update((blocks) => blocks.map((b) => (b.id === blockId ? { ...b, ...patch } : b)));
  }

  private async persistBlock(blockId: string, patch: Partial<DocumentBlock>) {
    if (!this.documentId || !this.locale) return;
    const current = this.blocksById().get(blockId);
    if (!current) return; // deleted locally before the debounce fired — nothing to save
    const { id, ...withoutId } = { ...current, ...patch };
    const saved = await this.contentService.replaceBlock(this.documentId, this.locale, blockId, withoutId);
    this.patchLocalBlock(blockId, saved);
  }
}
