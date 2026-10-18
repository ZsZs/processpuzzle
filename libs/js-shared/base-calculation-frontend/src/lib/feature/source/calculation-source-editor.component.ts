import { Component, effect, ElementRef, input, OnDestroy, output, viewChild, afterNextRender } from '@angular/core';
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands';
import { EditorState } from '@codemirror/state';
import { EditorView, keymap, lineNumbers } from '@codemirror/view';

/**
 * A CodeMirror editor for a calculation's YAML source — the same CodeMirror base-entity's RSQL editor builds on,
 * without a language package: the source is checked by the backend's Validate, which knows FEEL, rather than by a
 * YAML grammar in the browser. Tab indents, as YAML wants.
 *
 * `value` in, `valueChange` out. A new `value` that differs from what is being edited replaces it — a reload, or
 * the table editor rewriting the source — and one equal to it is the editor's own echo, which is ignored.
 */
@Component({
  selector: 'pp-calculation-source-editor',
  standalone: true,
  template: `<div #host class="pp-calculation-source-editor" data-testid="calculation-source-editor"></div>`,
  styles: `
    .pp-calculation-source-editor {
      border: 1px solid var(--pp-border-color, #ced4da);
      border-radius: 4px;
      font-family: 'JetBrains Mono', 'Fira Code', monospace;
      font-size: 13px;
      height: 520px;
      overflow: hidden;
      resize: vertical;
      background: #ffffff;
    }
    .pp-calculation-source-editor :global(.cm-editor) {
      height: 100%;
    }
  `,
})
export class CalculationSourceEditorComponent implements OnDestroy {
  readonly value = input<string>('');
  readonly readOnly = input(false);
  readonly valueChange = output<string>();

  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('host');
  private view?: EditorView;

  constructor() {
    afterNextRender(() => {
      this.view = new EditorView({
        parent: this.host().nativeElement,
        state: EditorState.create({
          doc: this.value(),
          extensions: [
            lineNumbers(),
            history(),
            keymap.of([indentWithTab, ...defaultKeymap, ...historyKeymap]),
            EditorState.tabSize.of(2),
            EditorState.readOnly.of(this.readOnly()),
            EditorView.updateListener.of((update) => {
              if (update.docChanged) this.valueChange.emit(update.state.doc.toString());
            }),
          ],
        }),
      });
    });
    effect(() => {
      const value = this.value();
      const view = this.view;
      if (view && view.state.doc.toString() !== value) {
        view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } });
      }
    });
  }

  /** The text being edited — what a caller saves. */
  text(): string {
    return this.view?.state.doc.toString() ?? this.value();
  }

  ngOnDestroy(): void {
    this.view?.destroy();
  }
}
