import { AfterViewInit, Component, computed, effect, ElementRef, input, OnDestroy, output, signal, untracked, viewChild } from '@angular/core';
import { Editor, Extensions, JSONContent } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { RichTextToolbarComponent } from './rich-text-toolbar.component';

/** Rich text as Tiptap's JSON document — `{ type: 'doc', content: [...] }` — the format it is stored in. */
export type RichTextContent = JSONContent;

/** What an editor without content of its own starts with: one empty paragraph, so the caret has a home. */
export const EMPTY_RICH_TEXT: RichTextContent = { type: 'doc', content: [{ type: 'paragraph' }] };

/** True when the document holds no text and no leaf node such as a horizontal rule — what an untouched editor holds. */
export function isRichTextEmpty(content: RichTextContent | null | undefined): boolean {
  if (!content) return true;
  if (typeof content.text === 'string' && content.text.trim().length > 0) return false;
  if (content.type && !['doc', 'paragraph', 'text', 'hardBreak'].includes(content.type) && !content.content?.length) return false;
  return (content.content ?? []).every((child) => isRichTextEmpty(child));
}

/**
 * One Tiptap `Editor` on StarterKit, with the formatting toolbar above it while it is editable.
 *
 * Content travels as Tiptap JSON rather than HTML: it is stored that way, and rendering it back through an
 * editor means only nodes StarterKit (plus `extensions`) defines can ever appear — there is no markup to
 * sanitize. With `editable` off the same component is the read-only rendering, without the toolbar.
 *
 * The toolbar sits in the flow rather than over the text, as base-document's floating one does: in a narrow form
 * column it wraps onto a second row, and a floating bar would then cover the first line being typed.
 *
 * `content` is the initial document and may be replaced later; a value equal to what the editor already holds
 * is ignored, so feeding `contentChange` straight back does not reset the caret.
 */
@Component({
  selector: 'pp-rich-text-editor',
  standalone: true,
  imports: [RichTextToolbarComponent],
  host: {
    '[class.pp-rich-text-editor--readonly]': '!editable()',
  },
  template: `
    @if (isToolbarVisible()) {
      <pp-rich-text-toolbar [editor]="editorInstance()!" [testIdPrefix]="testIdPrefix()" />
    }
    <div class="pp-rich-text-editor__body">
      @if (isPlaceholderVisible()) {
        <span class="pp-rich-text-editor__placeholder" aria-hidden="true">{{ placeholder() }}</span>
      }
      <div #editorHost class="pp-rich-text-editor" [attr.data-testid]="testIdPrefix() + '-content'"></div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        padding: 6px 8px 8px;
        border-radius: 4px;
        outline: 1px solid rgba(0, 0, 0, 0.12);
      }
      :host(:focus-within) {
        outline-color: rgba(0, 0, 0, 0.38);
      }
      :host(.pp-rich-text-editor--readonly) {
        padding: 0;
        outline: none;
      }
      /* The toolbar's own styles float it over a block's reserved strip; here it takes its place in the flow. */
      pp-rich-text-toolbar {
        position: static;
        display: block;
        margin-bottom: 6px;
      }
      .pp-rich-text-editor__body {
        position: relative;
      }
      .pp-rich-text-editor__placeholder {
        position: absolute;
        top: 0;
        left: 0;
        color: rgba(0, 0, 0, 0.42);
        pointer-events: none;
      }
      /* ProseMirror creates its contenteditable imperatively, so it carries no emulated-encapsulation attribute. */
      :host ::ng-deep .ProseMirror:focus {
        outline: none;
      }
      :host ::ng-deep .ProseMirror {
        min-height: 1.5em;
      }
      :host ::ng-deep .ProseMirror > :first-child {
        margin-top: 0;
      }
      :host ::ng-deep .ProseMirror > :last-child {
        margin-bottom: 0;
      }
    `,
  ],
})
export class RichTextEditorComponent implements AfterViewInit, OnDestroy {
  readonly content = input<RichTextContent | null | undefined>(undefined);
  readonly editable = input(true);
  readonly placeholder = input('');
  /** Added to StarterKit — e.g. base-document's widget embed. Read once, when the editor is created. */
  readonly extensions = input<Extensions>([]);
  /** Leading part of the test ids: `<prefix>-content` here, `<prefix>-toolbar`, `<prefix>-bold`, … on the toolbar. */
  readonly testIdPrefix = input('rich-text');
  readonly contentChange = output<RichTextContent>();

  /** Set once the Editor exists, so the toolbar — which needs one — can only render after ngAfterViewInit. */
  protected readonly editorInstance = signal<Editor | null>(null);
  protected readonly isEmpty = signal(true);
  protected readonly isToolbarVisible = computed(() => this.editable() && this.editorInstance() !== null);
  protected readonly isPlaceholderVisible = computed(() => this.editable() && this.isEmpty() && this.placeholder().length > 0);

  private readonly editorHost = viewChild.required<ElementRef<HTMLElement>>('editorHost');

  constructor() {
    effect(() => {
      const editor = this.editorInstance();
      const editable = this.editable();
      if (editor && editor.isEditable !== editable) editor.setEditable(editable, false);
    });
    effect(() => {
      const editor = this.editorInstance();
      const content = this.content() ?? EMPTY_RICH_TEXT;
      if (!editor) return;
      untracked(() => {
        if (JSON.stringify(editor.getJSON()) === JSON.stringify(content)) return;
        editor.commands.setContent(content, { emitUpdate: false });
        this.isEmpty.set(isRichTextEmpty(editor.getJSON()));
      });
    });
  }

  ngAfterViewInit() {
    const editor = new Editor({
      element: this.editorHost().nativeElement,
      editable: this.editable(),
      content: this.content() ?? EMPTY_RICH_TEXT,
      extensions: [StarterKit, ...this.extensions()],
      onUpdate: ({ editor }) => {
        const json = editor.getJSON();
        this.isEmpty.set(isRichTextEmpty(json));
        this.contentChange.emit(json);
      },
    });
    this.isEmpty.set(isRichTextEmpty(editor.getJSON()));
    this.editorInstance.set(editor);
  }

  ngOnDestroy() {
    this.editorInstance()?.destroy();
  }
}
