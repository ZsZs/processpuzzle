import { computed, Injectable, Signal, signal, WritableSignal } from '@angular/core';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { ArtifactBlock, WidgetPlacement } from '../../domain/base-artifact';
import { ArtifactContentService } from './artifact-content.service';

const TEXT_AUTOSAVE_DEBOUNCE_MS = 800;

/**
 * Owns block-level editing state for one open artifact, entirely separately from
 * BaseArtifactStore/BaseEntityStore — see the earlier discussion of why: continuous,
 * granular saves through the block endpoints must never be able to collide with the
 * Properties form's own save, which is why that form now hits updateArtifactProperties
 * instead of the whole-document PUT.
 *
 * Not a signalStore/NgRx composition on purpose — there's no list/form/tabs shape to
 * compose with here, just one array of blocks and some debounced side effects. A plain
 * injectable with signals is the honest fit.
 */
@Injectable()
export class ArtifactContentStore {
  private readonly blocksSignal: WritableSignal<ArtifactBlock[]> = signal([]);
  private artifactId: string | null = null;

  private readonly textSaveSubjects = new Map<string, Subject<Record<string, unknown>>>();

  constructor(private readonly contentService: ArtifactContentService) {}

  readonly blocks: Signal<ArtifactBlock[]> = this.blocksSignal.asReadonly();

  readonly blocksById: Signal<ReadonlyMap<string, ArtifactBlock>> = computed(() => {
    const map = new Map<string, ArtifactBlock>();
    for (const block of this.blocksSignal()) {
      map.set(block.id, block);
    }
    return map;
  });

  /** Called once when the host component loads the artifact — see ArtifactEditorComponent. */
  initialize(artifactId: string, blocks: ArtifactBlock[]) {
    this.artifactId = artifactId;
    this.blocksSignal.set(blocks);
  }

  /**
   * Host-context port resolution is deliberately out of scope here — base-artifact-api.yaml is
   * explicit that resolving a bound value is entirely a frontend runtime concern, and for an
   * artifact specifically that means whatever embeds it (an app-shell page, another artifact's
   * widget block) is responsible for supplying it. This placeholder returns undefined for every
   * port until that host-context wiring exists; it's the one seam ArtifactEditorComponent's
   * caller is expected to override once base-app's embedding story is designed.
   */
  resolveBinding(portName: string): unknown {
    return undefined;
  }

  /**
   * Debounced per block id so a fast typist doesn't fire a PUT per keystroke, but two different
   * blocks being edited near-simultaneously (unlikely today, single-editor-per-artifact, but the
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
   * caller to embed via a widgetEmbed node (see ArtifactEditorComponent's insert-widget command).
   */
  async appendReferencedWidget(type: string, props: Record<string, unknown> = {}): Promise<string> {
    if (!this.artifactId) throw new Error('ArtifactContentStore.initialize() was not called');
    const created = await this.contentService.appendBlock(this.artifactId, {
      kind: 'WIDGET' as ArtifactBlock['kind'],
      placement: WidgetPlacement.REFERENCED,
      type,
      props,
    });
    this.blocksSignal.update((blocks) => [...blocks, created]);
    return created.id;
  }

  async deleteBlock(blockId: string): Promise<void> {
    if (!this.artifactId) throw new Error('ArtifactContentStore.initialize() was not called');
    // Left to the caller: a 409 here means the widget is still referenced elsewhere (see
    // ArtifactBlockReferencedException) and the editor should surface that, not swallow it.
    await this.contentService.deleteBlock(this.artifactId, blockId);
    this.blocksSignal.update((blocks) => blocks.filter((b) => b.id !== blockId));
  }

  async reorder(blockIds: string[]): Promise<void> {
    if (!this.artifactId) throw new Error('ArtifactContentStore.initialize() was not called');
    const reordered = await this.contentService.reorderBlocks(this.artifactId, blockIds);
    this.blocksSignal.set(reordered);
  }

  private patchLocalBlock(blockId: string, patch: Partial<ArtifactBlock>) {
    this.blocksSignal.update((blocks) => blocks.map((b) => (b.id === blockId ? { ...b, ...patch } : b)));
  }

  private async persistBlock(blockId: string, patch: Partial<ArtifactBlock>) {
    if (!this.artifactId) return;
    const current = this.blocksById().get(blockId);
    if (!current) return; // deleted locally before the debounce fired — nothing to save
    const { id, ...withoutId } = { ...current, ...patch };
    const saved = await this.contentService.replaceBlock(this.artifactId, blockId, withoutId);
    this.patchLocalBlock(blockId, saved);
  }
}
