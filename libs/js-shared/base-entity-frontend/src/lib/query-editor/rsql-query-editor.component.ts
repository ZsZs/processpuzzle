import { AfterViewInit, Component, ElementRef, forwardRef, Inject, OnDestroy, output, signal, ViewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Compartment, EditorState } from '@codemirror/state';
import { EditorView, keymap, placeholder as cmPlaceholder } from '@codemirror/view';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { autocompletion, closeBrackets } from '@codemirror/autocomplete';
import { bracketMatching } from '@codemirror/language';

import { rsqlLanguage, rsqlSyntaxHighlighting } from './rsql-language';
import { createRsqlLinter } from './rsql-linter';
import { createRsqlCompletionSource } from './rsql-completion';
import { RsqlFieldMetadata, RsqlFieldMetadataProvider, RsqlFieldType } from './rsql-field-metadata.model';
import { TranslocoDirective } from '@jsverse/transloco';

const FALLBACK_SAMPLE = "status==active;createdAt=gt='2026-01-01'";
const SAMPLE_TYPE_PRIORITY: RsqlFieldType[] = ['string', 'date', 'datetime', 'enum', 'boolean', 'number'];

function buildSampleQuery(provider: RsqlFieldMetadataProvider): string {
  const fields = provider.getFields();
  if (fields.length === 0) return FALLBACK_SAMPLE;

  const picks: string[] = [];
  const used = new Set<string>();
  for (const type of SAMPLE_TYPE_PRIORITY) {
    if (picks.length >= 2) break;
    const field = fields.find((f) => f.type === type && !used.has(f.name));
    if (!field) continue;
    picks.push(sampleClause(field));
    used.add(field.name);
  }
  if (picks.length === 0) picks.push(`${fields[0].name}=='sample'`);
  return picks.join(';');
}

function sampleClause(field: RsqlFieldMetadata): string {
  switch (field.type) {
    case 'string': return `${field.name}=='sample'`;
    case 'number': return `${field.name}=gt=0`;
    case 'boolean': return `${field.name}==true`;
    case 'date':
    case 'datetime': return `${field.name}=gt='2026-01-01'`;
    case 'enum': return `${field.name}==${field.enumValues?.[0] ?? 'value'}`;
  }
}

@Component({
  selector: 'pp-rsql-query-editor',
  standalone: true,
  template: `<div #editorHost class="rsql-editor-host"></div>`,
  styles: [
    `
      .rsql-editor-host {
        border: 1px solid var(--pp-border-color, #ced4da);
        border-radius: 4px;
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: 13px;
        resize: both;
        overflow: hidden;
        min-width: 320px;
        min-height: 2.5rem;
        height: 180px;
        width: 100%;
      }
      .rsql-editor-host :global(.cm-editor) {
        height: 100%;
        min-height: 2.5rem;
      }
      .rsql-editor-host :global(.cm-editor.cm-focused) {
        outline: 2px solid var(--pp-focus-color, #339af0);
        outline-offset: -1px;
      }
    `,
  ],
  providers: [
    TranslocoDirective,
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => RsqlQueryEditorComponent),
      multi: true,
    },
  ],
})
export class RsqlQueryEditorComponent implements AfterViewInit, OnDestroy, ControlValueAccessor {
  @ViewChild('editorHost', { static: true }) editorHost!: ElementRef<HTMLDivElement>;

  /** Emits the current query string on every change (in addition to the CVA path). */
  readonly queryChange = output<string>();
  /** Emits true/false as validity flips, so parent forms can react without re-parsing. */
  readonly validityChange = output<boolean>();

  readonly isValid = signal(true);

  private view?: EditorView;
  private readOnlyCompartment = new Compartment();
  private onChange: (value: string) => void = () => {
    /* replaced by registerOnChange */
  };
  private onTouched: () => void = () => {
    /* replaced by registerOnTouched */
  };
  private pendingValue = '';

  constructor(@Inject(RsqlFieldMetadataProvider) private readonly fieldMetadata: RsqlFieldMetadataProvider) {}

  ngAfterViewInit(): void {
    const updateListener = EditorView.updateListener.of((update) => {
      if (update.docChanged) {
        const value = update.state.doc.toString();
        this.onChange(value);
        this.queryChange.emit(value);
      }
      if (update.transactions.some((tr) => tr.effects.length > 0)) {
        // diagnostics recompute is handled internally by the linter extension;
        // we just reflect current validity out for convenience.
        queueMicrotask(() => this.reportValidity());
      }
    });

    const sampleQuery = buildSampleQuery(this.fieldMetadata);
    const acceptSampleOnTab = {
      key: 'Tab',
      run: (view: EditorView) => {
        if (view.state.doc.length !== 0) return false;
        view.dispatch({
          changes: { from: 0, insert: sampleQuery },
          selection: { anchor: sampleQuery.length },
        });
        return true;
      },
    };

    const state = EditorState.create({
      doc: this.pendingValue,
      extensions: [
        history(),
        keymap.of([acceptSampleOnTab, ...defaultKeymap, ...historyKeymap]),
        rsqlLanguage,
        rsqlSyntaxHighlighting,
        bracketMatching(),
        closeBrackets(),
        autocompletion({ override: [createRsqlCompletionSource(this.fieldMetadata)] }),
        createRsqlLinter(this.fieldMetadata),
        cmPlaceholder(`e.g. ${sampleQuery}`),
        this.readOnlyCompartment.of([]),
        updateListener,
        EditorView.domEventHandlers({
          blur: () => {
            this.onTouched();
          },
        }),
      ],
    });

    this.view = new EditorView({ state, parent: this.editorHost.nativeElement });
    this.reportValidity();
  }

  ngOnDestroy(): void {
    this.view?.destroy();
  }

  // --- ControlValueAccessor ---

  writeValue(value: string | null): void {
    const next = value ?? '';
    this.pendingValue = next;
    if (this.view) {
      const current = this.view.state.doc.toString();
      if (current !== next) {
        this.view.dispatch({ changes: { from: 0, to: current.length, insert: next } });
      }
    }
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.view?.dispatch({
      effects: this.readOnlyCompartment.reconfigure(EditorView.editable.of(!isDisabled)),
    });
  }

  private reportValidity(): void {
    const view = this.view;
    if (!view) return;
    // @codemirror/lint stores active diagnostics on a state field we can read via
    // the lintState extension's export, but the simplest robust check is to
    // re-run the same validators used by the linter.
    import('@codemirror/lint').then(({ forEachDiagnostic }) => {
      let hasError = false;
      forEachDiagnostic(view.state, (d) => {
        if (d.severity === 'error') hasError = true;
      });
      const valid = !hasError;
      if (valid !== this.isValid()) {
        this.isValid.set(valid);
        this.validityChange.emit(valid);
      }
    });
  }
}
