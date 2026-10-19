import { Component, computed, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { PropsSchema, PropsSchemaProperty } from '@processpuzzle/widgets';

/** What the dialog is opened with: the block's widget as its registration describes it, and the block's current values. */
export interface WidgetBlockSettingsData {
  /** The widget's display name — the dialog's title. */
  name: string;
  schema?: PropsSchema;
  /** The widget's input ports, each of which can be bound to a path of the document. */
  inputPorts: ReadonlyArray<{ name: string; description?: string; required?: boolean }>;
  props: Record<string, unknown>;
  inputBindings: Record<string, string>;
  /** The document paths a port can be bound to: the document's own input ports and what they lead to. */
  paths: readonly string[];
}

export interface WidgetBlockSettings {
  props: Record<string, unknown>;
  inputBindings: Record<string, string>;
}

type FieldKind = 'text' | 'number' | 'boolean' | 'enum' | 'json';

/** One prop the dialog edits: a described scalar gets its own control, anything structured is edited as JSON. */
interface PropField {
  name: string;
  label: string;
  hint?: string;
  kind: FieldKind;
  options: readonly string[];
  required: boolean;
}

/**
 * Edits one widget block: its props, one control per property of the widget's schema, and which document path
 * feeds each of its input ports. Structured props — arrays and objects — are edited as JSON, because a generic
 * dialog cannot guess a row editor for them; a widget that needs a friendlier one, like `entity-table`, has its
 * own dialog. A widget nobody described gets a single JSON field for all its props.
 */
@Component({
  selector: 'pp-widget-block-settings-dialog',
  standalone: true,
  imports: [MatButton, MatCheckbox, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle, MatFormField, MatHint, MatInput, MatLabel, MatOption, MatSelect, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ data.name }}</h2>
    <mat-dialog-content class="pp-widget-settings">
      <h3>{{ 'base_document.document.widget_settings.props' | transloco }}</h3>
      @for (field of fields; track field.name) {
        @switch (field.kind) {
          @case ('boolean') {
            <mat-checkbox [checked]="draft()[field.name] === true" [attr.data-testid]="'widget-prop-' + field.name" (change)="setProp(field.name, $event.checked)">{{ field.label }}</mat-checkbox>
          }
          @case ('enum') {
            <mat-form-field>
              <mat-label>{{ field.label }}</mat-label>
              <mat-select [value]="draft()[field.name] ?? ''" [attr.data-testid]="'widget-prop-' + field.name" (selectionChange)="setProp(field.name, $event.value)">
                <mat-option value="">—</mat-option>
                @for (option of field.options; track option) {
                  <mat-option [value]="option">{{ option }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }
          @case ('json') {
            <mat-form-field>
              <mat-label>{{ field.label }}</mat-label>
              <textarea matInput rows="4" [value]="jsonText()[field.name] ?? ''" [attr.data-testid]="'widget-prop-' + field.name" (input)="setJson(field.name, $any($event.target).value)"></textarea>
              @if (jsonErrors()[field.name]) {
                <mat-hint class="pp-widget-settings__error">{{ 'base_document.document.widget_settings.invalid_json' | transloco }}</mat-hint>
              } @else if (field.hint) {
                <mat-hint>{{ field.hint }}</mat-hint>
              }
            </mat-form-field>
          }
          @default {
            <mat-form-field>
              <mat-label>{{ field.label }}</mat-label>
              <input
                matInput
                [type]="field.kind === 'number' ? 'number' : 'text'"
                [value]="draft()[field.name] ?? ''"
                [attr.data-testid]="'widget-prop-' + field.name"
                (input)="setProp(field.name, $any($event.target).value)"
              />
              @if (field.hint) {
                <mat-hint>{{ field.hint }}</mat-hint>
              }
            </mat-form-field>
          }
        }
      }
      @if (fields.length === 0) {
        <p class="pp-widget-settings__none" data-testid="widget-settings-no-props">{{ 'base_document.document.widget_settings.no_props' | transloco }}</p>
      }

      @if (data.inputPorts.length > 0) {
        <h3>{{ 'base_document.document.widget_settings.bindings' | transloco }}</h3>
        @for (port of data.inputPorts; track port.name) {
          <mat-form-field>
            <mat-label>{{ port.name }}</mat-label>
            <mat-select [value]="bindings()[port.name] ?? ''" [attr.data-testid]="'widget-binding-' + port.name" (selectionChange)="setBinding(port.name, $event.value)">
              <mat-option value="">—</mat-option>
              @for (path of data.paths; track path) {
                <mat-option [value]="path">{{ path }}</mat-option>
              }
            </mat-select>
            @if (port.description) {
              <mat-hint>{{ port.description }}</mat-hint>
            }
          </mat-form-field>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'base_document.document.report.cancel' | transloco }}</button>
      <button mat-flat-button type="button" data-testid="widget-settings-save" [disabled]="!valid()" (click)="onSave()">
        {{ 'base_document.document.report.apply' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .pp-widget-settings {
        display: flex;
        flex-direction: column;
        gap: 4px;
        min-width: 420px;
      }
      .pp-widget-settings__error {
        color: var(--mat-sys-error);
      }
      .pp-widget-settings__none {
        font-style: italic;
        opacity: 0.7;
      }
    `,
  ],
})
export class WidgetBlockSettingsDialogComponent {
  protected readonly data = inject<WidgetBlockSettingsData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<WidgetBlockSettingsDialogComponent, WidgetBlockSettings>>(MatDialogRef);

  protected readonly fields: readonly PropField[] = fieldsOf(this.data.schema, this.data.props);
  protected readonly draft = signal<Record<string, unknown>>({ ...this.data.props });
  protected readonly bindings = signal<Record<string, string>>({ ...this.data.inputBindings });
  /** The text of every JSON-edited prop, kept apart from the parsed value so half-typed JSON is not lost. */
  protected readonly jsonText = signal<Record<string, string>>(Object.fromEntries(this.fields.filter((field) => field.kind === 'json').map((field) => [field.name, jsonOf(this.data.props[field.name])])));
  protected readonly jsonErrors = signal<Record<string, boolean>>({});
  protected readonly valid = computed(() => !Object.values(this.jsonErrors()).some(Boolean));

  protected setProp(name: string, value: unknown): void {
    const field = this.fields.find((candidate) => candidate.name === name);
    const next = { ...this.draft() };
    if (value === '' || value === undefined) delete next[name];
    else next[name] = field?.kind === 'number' ? Number(value) : value;
    this.draft.set(next);
  }

  protected setJson(name: string, text: string): void {
    this.jsonText.update((current) => ({ ...current, [name]: text }));
    if (text.trim() === '') {
      this.jsonErrors.update((current) => ({ ...current, [name]: false }));
      this.setProp(name, undefined);
      return;
    }
    try {
      this.setProp(name, JSON.parse(text));
      this.jsonErrors.update((current) => ({ ...current, [name]: false }));
    } catch {
      this.jsonErrors.update((current) => ({ ...current, [name]: true }));
    }
  }

  protected setBinding(port: string, path: string): void {
    const next = { ...this.bindings() };
    if (path) next[port] = path;
    else delete next[port];
    this.bindings.set(next);
  }

  protected onSave(): void {
    this.dialogRef.close({ props: this.draft(), inputBindings: this.bindings() });
  }
}

/**
 * The props to edit: the schema's properties, or — for a widget nobody described — the props the block already
 * carries, all as JSON so that nothing it holds is dropped by a save.
 */
function fieldsOf(schema: PropsSchema | undefined, props: Record<string, unknown>): PropField[] {
  const required = new Set(schema?.required ?? []);
  if (schema?.properties) return Object.entries(schema.properties).map(([name, property]) => fieldOf(name, property, required.has(name)));
  return Object.keys(props).map((name) => ({ name, label: name, kind: 'json' as const, options: [], required: false }));
}

function fieldOf(name: string, property: PropsSchemaProperty, required: boolean): PropField {
  return { name, label: property.title ?? name, hint: property.description, kind: kindOf(property), options: property.enum ?? [], required };
}

function kindOf(property: PropsSchemaProperty): FieldKind {
  if (property.enum && property.type === 'string') return 'enum';
  switch (property.type) {
    case 'string':
      return 'text';
    case 'number':
    case 'integer':
      return 'number';
    case 'boolean':
      return 'boolean';
    default:
      return 'json';
  }
}

function jsonOf(value: unknown): string {
  return value === undefined ? '' : JSON.stringify(value, null, 2);
}
