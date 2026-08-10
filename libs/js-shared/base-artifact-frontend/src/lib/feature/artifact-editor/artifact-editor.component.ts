import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, Type } from '@angular/core';
import { WIDGET_REGISTRY } from '@processpuzzle/base-entity';
import { ArtifactBlock, BlockKind, WidgetPlacement } from '../../domain/base-artifact';
import { ArtifactContentStore } from './artifact-content.store';
import { ArtifactTextBlockComponent } from './artifact-text-block.component';

/**
 * The primary editing surface for an artifact's content — see BaseArtifactContainerComponent
 * for how this sits alongside the generic Properties form on one screen. Renders the flat
 * block list in order: TEXT blocks get their own ArtifactTextBlockComponent/Editor instance,
 * STANDALONE WIDGET blocks mount directly via NgComponentOutlet. REFERENCED WIDGET blocks are
 * deliberately absent from this top-level list — they only ever render inside whichever TEXT
 * block's Tiptap content embeds them via widgetEmbed (see ArtifactTextBlockComponent), which is
 * the one place base-artifact-api.yaml lets a REFERENCED block appear.
 */
@Component({
  selector: 'pp-artifact-editor',
  standalone: true,
  imports: [CommonModule, ArtifactTextBlockComponent],
  providers: [ArtifactContentStore],
  template: `
    <div class="pp-artifact-editor">
      @for (block of standaloneBlocks(); track block.id) {
        @switch (block.kind) {
          @case (blockKindText) {
            <pp-artifact-text-block [block]="block" />
          }
          @case (blockKindWidget) {
            <ng-container *ngComponentOutlet="componentFor(block); inputs: propsFor(block)" />
          }
        }
      }
    </div>
  `,
})
export class ArtifactEditorComponent {
  readonly artifactId = input.required<string>();
  readonly blocks = input.required<ArtifactBlock[]>();

  private readonly widgetRegistry = inject(WIDGET_REGISTRY, { optional: true }) ?? new Map<string, Type<unknown>>();
  private readonly contentStore = inject(ArtifactContentStore);

  protected readonly blockKindText = BlockKind.TEXT;
  protected readonly blockKindWidget = BlockKind.WIDGET;

  // Re-initializes the content store whenever a different artifact is loaded — cheap, since
  // ArtifactContentStore.initialize() is just two signal writes.
  private readonly initializeOnLoad = computed(() => {
    this.contentStore.initialize(this.artifactId(), this.blocks());
    return null;
  });

  // Renders from the store's own live signal (not the `blocks` input directly) once initialized,
  // so edits made through ArtifactContentStore — append/delete/reorder — are reflected without
  // the parent needing to re-fetch the whole artifact.
  protected readonly standaloneBlocks = computed(() => {
    this.initializeOnLoad(); // establish the dependency so this recomputes if artifactId/blocks input changes
    return this.contentStore.blocks().filter((b) => b.kind !== BlockKind.WIDGET || b.placement !== WidgetPlacement.REFERENCED);
  });

  protected componentFor(block: ArtifactBlock): Type<unknown> | null {
    return block.type ? this.widgetRegistry.get(block.type) ?? null : null;
  }

  protected propsFor(block: ArtifactBlock): Record<string, unknown> {
    // Same resolution rule as WidgetEmbedNodeView.applyProps: a declared input binding wins
    // over the matching static prop. Kept intentionally duplicated rather than shared, since
    // one is plain-object-in-a-template and the other runs inside a ProseMirror NodeView with
    // no template binding mechanism at all — a shared helper would need to abstract over that
    // difference for no real benefit at this size.
    const resolved: Record<string, unknown> = { ...block.props };
    for (const [widgetPropName, portName] of Object.entries(block.inputBindings ?? {})) {
      resolved[widgetPropName] = this.contentStore.resolveBinding(portName);
    }
    return resolved;
  }

  /** Called by a toolbar action to embed a new widget at the given text block's cursor. */
  async insertWidgetInto(textBlock: ArtifactTextBlockComponent, widgetType: string, props: Record<string, unknown> = {}) {
    const blockId = await this.contentStore.appendReferencedWidget(widgetType, props);
    textBlock.embedWidget(blockId);
  }
}
