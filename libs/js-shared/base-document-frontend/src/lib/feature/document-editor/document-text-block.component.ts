import { AfterViewInit, Component, DestroyRef, ElementRef, EnvironmentInjector, inject, input, OnDestroy, viewChild } from '@angular/core';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { WIDGET_REGISTRY } from '@processpuzzle/base-entity';
import { DocumentBlock } from '../../domain/base-document';
import { DocumentContentStore } from './document-content.store';
import { createWidgetEmbedExtension } from './widget-embed-node';

/**
 * One Tiptap Editor bound to exactly one TEXT block's own `content` field — not one editor for
 * the whole document. Each block already has its own replaceDocumentBlock endpoint; splitting
 * editors along the same boundary means a save touches exactly the JSON document it changed,
 * and a REFERENCED widget embedded via widgetEmbed always resolves relative to this one block's
 * surrounding prose, which is the only place base-document-api.yaml lets it appear.
 */
@Component({
  selector: 'pp-document-text-block',
  standalone: true,
  template: `<div #editorHost class="pp-document-text-block" [class.pp-document-text-block--readonly]="!block().editable"></div>`,
})
export class DocumentTextBlockComponent implements AfterViewInit, OnDestroy {
  readonly block = input.required<DocumentBlock>();

  private readonly editorHost = viewChild.required<ElementRef<HTMLElement>>('editorHost');
  private readonly environmentInjector = inject(EnvironmentInjector);
  private readonly widgetRegistry = inject(WIDGET_REGISTRY, { optional: true }) ?? new Map();
  private readonly contentStore = inject(DocumentContentStore);
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
   * cursor — called by DocumentEditorComponent's "insert widget" toolbar action after it has
   * awaited DocumentContentStore.appendReferencedWidget(). Kept as a public method rather than
   * wiring insertion through Tiptap commands from the parent, since the Editor instance is
   * private to this component by design.
   */
  embedWidget(blockId: string) {
    this.editor?.chain().focus().insertContent({ type: 'widgetEmbed', attrs: { blockId } }).run();
  }
}
