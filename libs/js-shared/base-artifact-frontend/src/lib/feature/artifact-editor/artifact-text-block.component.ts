import { AfterViewInit, Component, DestroyRef, ElementRef, EnvironmentInjector, inject, input, OnDestroy, viewChild } from '@angular/core';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { WIDGET_REGISTRY } from '@processpuzzle/base-entity';
import { ArtifactBlock } from '../../domain/base-artifact';
import { ArtifactContentStore } from './artifact-content.store';
import { createWidgetEmbedExtension } from './widget-embed-node';

/**
 * One Tiptap Editor bound to exactly one TEXT block's own `content` field — not one editor for
 * the whole artifact. Each block already has its own replaceArtifactBlock endpoint; splitting
 * editors along the same boundary means a save touches exactly the JSON document it changed,
 * and a REFERENCED widget embedded via widgetEmbed always resolves relative to this one block's
 * surrounding prose, which is the only place base-artifact-api.yaml lets it appear.
 */
@Component({
  selector: 'pp-artifact-text-block',
  standalone: true,
  template: `<div #editorHost class="pp-artifact-text-block" [class.pp-artifact-text-block--readonly]="!block().editable"></div>`,
})
export class ArtifactTextBlockComponent implements AfterViewInit, OnDestroy {
  readonly block = input.required<ArtifactBlock>();

  private readonly editorHost = viewChild.required<ElementRef<HTMLElement>>('editorHost');
  private readonly environmentInjector = inject(EnvironmentInjector);
  private readonly widgetRegistry = inject(WIDGET_REGISTRY, { optional: true }) ?? new Map();
  private readonly contentStore = inject(ArtifactContentStore);
  private readonly destroyRef = inject(DestroyRef);

  private editor: Editor | null = null;

  ngAfterViewInit() {
    const block = this.block();
    this.editor = new Editor({
      element: this.editorHost().nativeElement,
      editable: block.editable !== false,
      content: block.content ?? { type: 'doc', content: [{ type: 'paragraph' }] },
      extensions: [
        StarterKit,
        createWidgetEmbedExtension({
          environmentInjector: this.environmentInjector,
          widgetRegistry: this.widgetRegistry,
          blocksById: this.contentStore.blocksById,
          resolveBinding: (portName) => this.contentStore.resolveBinding(portName),
        }),
      ],
      onUpdate: ({ editor }) => {
        this.contentStore.saveTextBlockContent(block.id, editor.getJSON());
      },
    });

    this.destroyRef.onDestroy(() => this.editor?.destroy());
  }

  ngOnDestroy() {
    this.editor?.destroy();
  }

  /**
   * Inserts a widgetEmbed node referencing an already-created REFERENCED block at the current
   * cursor — called by ArtifactEditorComponent's "insert widget" toolbar action after it has
   * awaited ArtifactContentStore.appendReferencedWidget(). Kept as a public method rather than
   * wiring insertion through Tiptap commands from the parent, since the Editor instance is
   * private to this component by design.
   */
  embedWidget(blockId: string) {
    this.editor?.chain().focus().insertContent({ type: 'widgetEmbed', attrs: { blockId } }).run();
  }
}
