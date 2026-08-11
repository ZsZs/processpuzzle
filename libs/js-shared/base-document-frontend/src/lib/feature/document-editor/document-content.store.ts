import { computed, Injectable, Signal, signal, WritableSignal } from '@angular/core';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { DocumentBlock, WidgetPlacement } from '../../domain/base-document';
import { DocumentContentService } from './document-content.service';

const TEXT_AUTOSAVE_DEBOUNCE_MS = 800;

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

  private readonly textSaveSubjects = new Map<string, Subject<Record<string, unknown>>>();

  constructor(private readonly contentService: DocumentContentService) {}

  readonly blocks: Signal<DocumentBlock[]> = this.blocksSignal.asReadonly();

  readonly blocksById: Signal<ReadonlyMap<string, DocumentBlock>> = computed(() => {
    const map = new Map<string, DocumentBlock>();
    for (const block of this.blocksSignal()) {
      map.set(block.id, block);
    }
    return map;
  });

  /** Called once when the host component loads the document — see DocumentEditorComponent. */
  initialize(documentId: string, blocks: DocumentBlock[]) {
    this.documentId = documentId;
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
   * Inserts a new REFERENCED widget block and returns its server-assigned id, ready for the
   * caller to embed via a widgetEmbed node (see DocumentEditorComponent's insert-widget command).
   */
  async appendReferencedWidget(type: string, props: Record<string, unknown> = {}): Promise<string> {
    if (!this.documentId) throw new Error('DocumentContentStore.initialize() was not called');
    const created = await this.contentService.appendBlock(this.documentId, {
      kind: 'WIDGET' as DocumentBlock['kind'],
      placement: WidgetPlacement.REFERENCED,
      type,
      props,
    });
    this.blocksSignal.update((blocks) => [...blocks, created]);
    return created.id;
  }

  async deleteBlock(blockId: string): Promise<void> {
    if (!this.documentId) throw new Error('DocumentContentStore.initialize() was not called');
    // Left to the caller: a 409 here means the widget is still referenced elsewhere (see
    // DocumentBlockReferencedException) and the editor should surface that, not swallow it.
    await this.contentService.deleteBlock(this.documentId, blockId);
    this.blocksSignal.update((blocks) => blocks.filter((b) => b.id !== blockId));
  }

  async reorder(blockIds: string[]): Promise<void> {
    if (!this.documentId) throw new Error('DocumentContentStore.initialize() was not called');
    const reordered = await this.contentService.reorderBlocks(this.documentId, blockIds);
    this.blocksSignal.set(reordered);
  }

  private patchLocalBlock(blockId: string, patch: Partial<DocumentBlock>) {
    this.blocksSignal.update((blocks) => blocks.map((b) => (b.id === blockId ? { ...b, ...patch } : b)));
  }

  private async persistBlock(blockId: string, patch: Partial<DocumentBlock>) {
    if (!this.documentId) return;
    const current = this.blocksById().get(blockId);
    if (!current) return; // deleted locally before the debounce fired — nothing to save
    const { id, ...withoutId } = { ...current, ...patch };
    const saved = await this.contentService.replaceBlock(this.documentId, blockId, withoutId);
    this.patchLocalBlock(blockId, saved);
  }
}
