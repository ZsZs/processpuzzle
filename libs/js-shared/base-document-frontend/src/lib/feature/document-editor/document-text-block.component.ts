import { AfterViewInit, Component, computed, DestroyRef, ElementRef, EnvironmentInjector, inject, input, OnDestroy, signal, viewChild } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { RichTextToolbarComponent } from '@processpuzzle/base-entity';
import { WIDGET_REGISTRY, WidgetRegistration } from '@processpuzzle/widgets';
import { firstValueFrom } from 'rxjs';
import { DocumentBlock } from '../../domain/base-document';
import { DocumentPathCatalog } from '../report/document-path-catalog';
import { DataFieldChoice, DataFieldDialogComponent } from './data-field-dialog.component';
import { createDataFieldExtension, DATA_FIELD_NODE } from './data-field-node';
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
  imports: [MatIcon, MatTooltip, RichTextToolbarComponent, TranslocoPipe],
  template: `
    @if (isToolbarVisible()) {
      <pp-rich-text-toolbar [editor]="editorInstance()!" testIdPrefix="document-text-block" />
      @if (hasPorts()) {
        <button
          type="button"
          class="pp-document-text-block__insert-field"
          data-testid="document-text-block-insert-field"
          [matTooltip]="'base_document.document.report.insert_field' | transloco"
          (mousedown)="$event.preventDefault()"
          (click)="onInsertField()"
        >
          <mat-icon>data_object</mat-icon>
        </button>
      }
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
      /* Opposite the formatting toolbar, in the same reserved strip. */
      .pp-document-text-block__insert-field {
        position: absolute;
        top: 1px;
        right: 4px;
        z-index: 5;
        display: inline-flex;
        padding: 2px;
        border: none;
        border-radius: 4px;
        background: transparent;
        cursor: pointer;
      }
      .pp-document-text-block__insert-field:hover {
        background: rgba(0, 0, 0, 0.08);
      }
      /* Unresolved only while authoring: the path the field will print, so the author sees what goes where. */
      :host ::ng-deep .pp-data-field--unresolved {
        padding: 0 4px;
        border: 1px dashed rgba(0, 0, 0, 0.38);
        border-radius: 4px;
        font-family: monospace;
        font-size: 0.9em;
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
  private readonly widgetRegistry = inject(WIDGET_REGISTRY, { optional: true }) ?? new Map<string, WidgetRegistration>();
  private readonly contentStore = inject(DocumentContentStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly pathCatalog = inject(DocumentPathCatalog);
  private readonly transloco = inject(TranslocoService);

  /** A field needs a port to come from; a document without input ports has nothing to insert. */
  protected readonly hasPorts = computed(() => this.contentStore.inputPorts().length > 0);

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
          publishOutput: (event) => this.contentStore.publishOutput(event.port, event.value),
        }),
        createDataFieldExtension({
          environmentInjector: this.environmentInjector,
          resolveBinding: (path) => this.contentStore.resolveBinding(path),
          locale: () => this.contentStore.currentLocale() ?? this.transloco.getActiveLang(),
          editable: block.editable !== false,
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

  /**
   * Asks which value to place, then inserts a `dataField` node where the caret was. `focus()` before the
   * insert restores the selection the dialog took focus from.
   */
  protected async onInsertField(): Promise<void> {
    const { fields } = await this.pathCatalog.describe(this.contentStore.inputPorts());
    const choice = await firstValueFrom(this.dialog.open<DataFieldDialogComponent, unknown, DataFieldChoice>(DataFieldDialogComponent, { data: { fields } }).afterClosed());
    if (choice) this.insertField(choice);
  }

  insertField(choice: DataFieldChoice) {
    this.editor?.chain().focus().insertContent({ type: DATA_FIELD_NODE, attrs: { path: choice.path, format: choice.format } }).run();
  }
}
