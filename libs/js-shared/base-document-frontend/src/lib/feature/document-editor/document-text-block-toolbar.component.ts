import { Component, computed, effect, input, signal } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslocoPipe } from '@jsverse/transloco';
import { Editor } from '@tiptap/core';

/** One button: what it looks like, when it is lit, and what it does to the block being edited. */
interface ToolbarAction {
  /** Doubles as the i18n key under `document.content.toolbar` and as the test-id suffix. */
  readonly name: string;
  readonly icon: string;
  /** Absent for actions with no on/off state — undo has nothing to be "on". */
  readonly activeWhen?: (editor: Editor) => boolean;
  /** Absent for actions that are always available. */
  readonly enabledWhen?: (editor: Editor) => boolean;
  readonly apply: (editor: Editor) => void;
  /** Draws a separator before this button, grouping marks / blocks / lists / history. */
  readonly startsGroup?: boolean;
}

/**
 * Only what StarterKit actually registers — the extension list in DocumentTextBlockComponent is the
 * contract here, so there is no Link or Underline button for a mark no editor would accept.
 */
const TOOLBAR_ACTIONS: readonly ToolbarAction[] = [
  { name: 'bold', icon: 'format_bold', activeWhen: (e) => e.isActive('bold'), apply: (e) => e.chain().focus().toggleBold().run() },
  { name: 'italic', icon: 'format_italic', activeWhen: (e) => e.isActive('italic'), apply: (e) => e.chain().focus().toggleItalic().run() },
  { name: 'strike', icon: 'format_strikethrough', activeWhen: (e) => e.isActive('strike'), apply: (e) => e.chain().focus().toggleStrike().run() },
  { name: 'code', icon: 'code', activeWhen: (e) => e.isActive('code'), apply: (e) => e.chain().focus().toggleCode().run() },
  { name: 'heading_1', icon: 'looks_one', startsGroup: true, activeWhen: (e) => e.isActive('heading', { level: 1 }), apply: (e) => e.chain().focus().toggleHeading({ level: 1 }).run() },
  { name: 'heading_2', icon: 'looks_two', activeWhen: (e) => e.isActive('heading', { level: 2 }), apply: (e) => e.chain().focus().toggleHeading({ level: 2 }).run() },
  { name: 'heading_3', icon: 'looks_3', activeWhen: (e) => e.isActive('heading', { level: 3 }), apply: (e) => e.chain().focus().toggleHeading({ level: 3 }).run() },
  { name: 'bullet_list', icon: 'format_list_bulleted', startsGroup: true, activeWhen: (e) => e.isActive('bulletList'), apply: (e) => e.chain().focus().toggleBulletList().run() },
  { name: 'ordered_list', icon: 'format_list_numbered', activeWhen: (e) => e.isActive('orderedList'), apply: (e) => e.chain().focus().toggleOrderedList().run() },
  { name: 'blockquote', icon: 'format_quote', activeWhen: (e) => e.isActive('blockquote'), apply: (e) => e.chain().focus().toggleBlockquote().run() },
  { name: 'horizontal_rule', icon: 'horizontal_rule', apply: (e) => e.chain().focus().setHorizontalRule().run() },
  { name: 'undo', icon: 'undo', startsGroup: true, enabledWhen: (e) => e.can().undo(), apply: (e) => e.chain().focus().undo().run() },
  { name: 'redo', icon: 'redo', enabledWhen: (e) => e.can().redo(), apply: (e) => e.chain().focus().redo().run() },
];

/**
 * The formatting bar for the one TEXT block currently being edited. It is per block rather than one bar for
 * the whole document because every command it issues targets a single Tiptap `Editor`, and this library gives
 * each block its own (see DocumentTextBlockComponent) — a document-level bar would have to track which of N
 * editors last had the caret, and would sit a screen away from it in a long document. Rendered into the strip
 * DocumentTextBlockComponent reserves above each block, so appearing and disappearing shifts nothing.
 *
 * `mousedown` is cancelled on every button: pressing one must not move focus out of the editor, or the command
 * would run against a collapsed, blurred selection and the caret would be lost after each click.
 */
@Component({
  selector: 'pp-document-text-block-toolbar',
  standalone: true,
  imports: [MatIcon, MatTooltip, TranslocoPipe],
  template: `
    <div class="pp-block-toolbar" role="toolbar" [attr.aria-label]="'base_document.document.content.toolbar.label' | transloco" data-testid="document-text-block-toolbar">
      @for (action of actions; track action.name) {
        @if (action.startsGroup) {
          <span class="pp-block-toolbar__separator"></span>
        }
        <button
          type="button"
          class="pp-block-toolbar__button"
          [class.pp-block-toolbar__button--active]="activeActions().has(action.name)"
          [disabled]="disabledActions().has(action.name)"
          [attr.aria-pressed]="action.activeWhen ? activeActions().has(action.name) : null"
          [attr.data-testid]="testIdOf(action)"
          [matTooltip]="'base_document.document.content.toolbar.' + action.name | transloco"
          (mousedown)="$event.preventDefault()"
          (click)="onApply(action)"
        >
          <mat-icon>{{ action.icon }}</mat-icon>
        </button>
      }
    </div>
  `,
  styles: [
    `
      :host {
        position: absolute;
        top: 1px;
        left: 4px;
        z-index: 5;
      }
      .pp-block-toolbar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 2px;
        padding: 2px 4px;
        border: 1px solid rgba(0, 0, 0, 0.12);
        border-radius: 6px;
        background-color: #ffffff;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.16);
      }
      .pp-block-toolbar__button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 26px;
        height: 26px;
        padding: 0;
        border: none;
        border-radius: 4px;
        background: transparent;
        color: rgba(0, 0, 0, 0.72);
        cursor: pointer;
      }
      .pp-block-toolbar__button:hover:not(:disabled) {
        background-color: rgba(0, 0, 0, 0.06);
      }
      .pp-block-toolbar__button:disabled {
        opacity: 0.38;
        cursor: default;
      }
      .pp-block-toolbar__button--active {
        background-color: rgba(24, 111, 206, 0.14);
        color: var(--pp-color-dark-blue, rgb(24, 111, 206));
      }
      .pp-block-toolbar__button mat-icon {
        width: 18px;
        height: 18px;
        font-size: 18px;
        line-height: 18px;
      }
      .pp-block-toolbar__separator {
        width: 1px;
        height: 18px;
        margin: 0 2px;
        background-color: rgba(0, 0, 0, 0.12);
      }
    `,
  ],
})
export class DocumentTextBlockToolbarComponent {
  readonly editor = input.required<Editor>();

  protected readonly actions = TOOLBAR_ACTIONS;

  /**
   * Bumped on every ProseMirror transaction, which is what makes the lit buttons follow the caret: Tiptap's
   * state lives outside Angular, so `isActive` has nothing to depend on until a signal stands in for it.
   */
  private readonly revision = signal(0);

  constructor() {
    effect((onCleanup) => {
      const editor = this.editor();
      const bump = () => this.revision.update((value) => value + 1);
      editor.on('transaction', bump);
      onCleanup(() => editor.off('transaction', bump));
    });
  }

  protected readonly activeActions = computed(() => this.namesMatching((action, editor) => action.activeWhen?.(editor) === true));
  protected readonly disabledActions = computed(() => this.namesMatching((action, editor) => action.enabledWhen?.(editor) === false));

  protected testIdOf(action: ToolbarAction): string {
    return `document-text-block-${action.name.replaceAll('_', '-')}`;
  }

  protected onApply(action: ToolbarAction): void {
    action.apply(this.editor());
  }

  private namesMatching(predicate: (action: ToolbarAction, editor: Editor) => boolean): Set<string> {
    this.revision();
    const editor = this.editor();
    return new Set(TOOLBAR_ACTIONS.filter((action) => predicate(action, editor)).map((action) => action.name));
  }
}
