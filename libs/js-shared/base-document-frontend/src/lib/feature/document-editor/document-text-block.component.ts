import { AfterViewInit, Component, computed, DestroyRef, ElementRef, EnvironmentInjector, inject, input, OnDestroy, signal, viewChild } from '@angular/core';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { WIDGET_REGISTRY } from '@processpuzzle/base-entity';
import { DocumentBlock } from '../../domain/base-document';
import { DocumentContentStore } from './document-content.store';
import { DocumentTextBlockToolbarComponent } from './document-text-block-toolbar.component';
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
  imports: [DocumentTextBlockToolbarComponent],
  template: `
    @if (isToolbarVisible()) {
      <pp-document-text-block-toolbar [editor]="editorInstance()!" />
    }
    <div #editorHost class="pp-document-text-block" [class.pp-document-text-block--readonly]="!block().editable"></div>
  `,
  styles: [
    `
      /*
       * The reserved top strip is what the toolbar is drawn into. Reserving it unconditionally — rather than
       * letting the toolbar take flow space when it appears — is what keeps the prose from jumping down under
       * the caret the moment the block is focused; it doubles as the gap between blocks.
       */
      :host {
        display: block;
        position: relative;
        padding: 32px 8px 8px;
        border-radius: 4px;
        /* An outline rather than a border: it is drawn outside the box, so lighting it up moves nothing. */
        outline: 1px solid transparent;
        transition: outline-color 120ms ease-in-out;
      }
      :host(:hover) {
        outline-color: rgba(0, 0, 0, 0.12);
      }
      :host(:focus-within) {
        outline-color: rgba(0, 0, 0, 0.24);
      }
      /*
       * ::ng-deep is unavoidable here: ProseMirror creates its own contenteditable element imperatively, so it
       * carries none of this component's emulated-encapsulation attributes. Suppresses the browser's own focus
       * ring, which would otherwise draw a second frame inside the block frame above.
       */
      :host ::ng-deep .ProseMirror:focus {
        outline: none;
      }
      /* An empty block still has to be a visible, clickable extent rather than a zero-height line. */
      :host ::ng-deep .ProseMirror {
        min-height: 1.5em;
      }
    `,
  ],
})
export class DocumentTextBlockComponent implements AfterViewInit, OnDestroy {
  readonly block = input.required<DocumentBlock>();

  /** Set once the Editor exists, so the toolbar — which needs one — can only render after ngAfterViewInit. */
  protected readonly editorInstance = signal<Editor | null>(null);
  protected readonly isEditing = signal(false);
  protected readonly isToolbarVisible = computed(() => this.isEditing() && this.editorInstance() !== null);

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
      // Focus, not hover, is what "being edited" means — the toolbar has to stay up while the pointer travels
      // to it, and buttons cancel their mousedown so reaching for one does not blur the editor.
      onFocus: () => this.isEditing.set(true),
      onBlur: () => this.isEditing.set(false),
    });
    this.editorInstance.set(this.editor);

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
