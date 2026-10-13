import { AfterViewInit, Component, DestroyRef, inject, signal, viewChild, ViewContainerRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormGroup } from '@angular/forms';
import { BaseEntity, BaseEntityAttrDescriptor, BaseFormControlComponent, EntityLabelPipe, FormControlType } from '@processpuzzle/base-entity';
import { distinctUntilChanged, Subscription } from 'rxjs';
import { ARTIFACT_FORMAT, hasDescribedProps, propsSchemaToDescriptors } from '../widget-definition/props-schema-to-descriptors';
import { PropsSchema, PropsSchemaProperty } from '../widget-definition/widget-definition';
import { WIDGET_REGISTRY, WidgetRegistration } from '../widget-registry/widget-registry.token';

/**
 * The `props` attribute of a widget placement, edited through a form generated from the placement's widget
 * type rather than as open key/value pairs. See {@link WidgetPropsControlComponent}.
 *
 * `typeAttrName` names the sibling attribute holding the widget type — `type` on a `WidgetInstance`.
 */
export class WidgetPropsAttrDescriptor extends BaseEntityAttrDescriptor {
  constructor(
    attrName = 'props',
    readonly typeAttrName = 'type',
    label = 'Props',
  ) {
    super(attrName, FormControlType.CUSTOM, label);
    this.component = WidgetPropsControlComponent;
  }
}

type PropsMode = 'schema' | 'no-props' | 'open';

/**
 * Edits a placement's props with one typed control per property of the selected widget type's `propsSchema`
 * — an ARTIFACT control for an artifact-valued prop, a dropdown for an enum, and so on — and rebuilds the
 * controls when the type changes.
 *
 * The schema is read from the {@link WIDGET_REGISTRY}, so it is the one the component that will render the
 * placement was registered with. Three states:
 *
 * - **schema** — a nested form, whose value is written back to the `props` control as it changes: only the
 *   described properties, empty ones omitted, numbers as numbers. Its validity is the `props` control's.
 * - **no props** — the type's schema declares none; there is nothing to edit.
 * - **open** — no registered type, or one nobody described: the open key/value editor, as before.
 */
@Component({
  selector: 'pp-widget-props-control',
  standalone: true,
  imports: [EntityLabelPipe],
  // One host in every mode, so switching modes never re-creates the element the controls are built into.
  template: `
    <div class="base-entity-form-field" [class.pp-widget-props]="mode() === 'schema'">
      @if (mode() === 'schema') {
        <span class="pp-widget-props__label">{{ config().i18nKey() | ppLabel: config().label }}</span>
      } @else if (mode() === 'no-props') {
        <p class="pp-widget-props__none" data-testid="widget-props-none">This widget takes no props.</p>
      }
      <ng-container #host />
    </div>
  `,
  styles: [
    `
      .pp-widget-props {
        border: 1px solid var(--mat-sys-outline-variant);
        border-radius: 4px;
        display: flex;
        flex-direction: column;
        gap: 5px;
        padding: 8px 12px;
      }
      .pp-widget-props__label {
        font: var(--mat-sys-label-large);
      }
      .pp-widget-props__none {
        font-style: italic;
        opacity: 0.7;
      }
    `,
  ],
})
export class WidgetPropsControlComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements AfterViewInit {
  protected readonly mode = signal<PropsMode>('open');

  private readonly host = viewChild.required('host', { read: ViewContainerRef });
  private readonly registry = inject(WIDGET_REGISTRY, { optional: true }) ?? new Map<string, WidgetRegistration>();
  private readonly destroyRef = inject(DestroyRef);
  private propsSubscription?: Subscription;

  ngAfterViewInit(): void {
    const typeControl = this.formGroup.get((this.config() as WidgetPropsAttrDescriptor).typeAttrName ?? 'type');
    this.build(typeControl?.value);
    typeControl?.valueChanges.pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef)).subscribe((type) => this.build(type));
    this.destroyRef.onDestroy(() => this.propsSubscription?.unsubscribe());
  }

  private build(type: string | undefined): void {
    this.propsSubscription?.unsubscribe();
    this.host().clear();
    const propsControl = this.formGroup.get(this.config().attrName);
    const schema = type ? this.registry.get(type)?.definition.propsSchema : undefined;
    propsControl?.clearValidators();
    propsControl?.updateValueAndValidity({ emitEvent: false });

    if (!schema) this.mode.set('open');
    else if (!hasDescribedProps(schema)) this.mode.set('no-props');
    else this.mode.set('schema');
    this.fill(schema, propsControl);
  }

  private fill(schema: PropsSchema | undefined, propsControl: AbstractControl | null): void {
    const host = this.host();
    if (!propsControl) return;
    host.clear();
    if (this.mode() === 'open') {
      // Built on the entity's own form group: `addControl` keeps the existing `props` control, so the open
      // editor edits it in place.
      const openEditor = new BaseEntityAttrDescriptor(this.config().attrName, FormControlType.ADDITIONAL_PROPERTIES, this.config().label);
      this.formBuilder.buildForm(host, this.formGroup, this.store, [openEditor], signal(this.entity()), this.entityName());
      return;
    }
    if (this.mode() !== 'schema' || !schema) return;

    const propsForm = new FormGroup({});
    const current = (propsControl.value ?? {}) as Entity;
    this.formBuilder.buildForm(host, propsForm, this.store, propsSchemaToDescriptors(schema), signal(current), this.entityName());
    propsControl.setValidators(() => (propsForm.invalid ? { widgetProps: true } : null));
    propsControl.updateValueAndValidity({ emitEvent: false });
    this.propsSubscription = propsForm.valueChanges.subscribe(() => {
      propsControl.setValue(toProps(propsForm.getRawValue(), schema));
      propsControl.markAsDirty();
    });
  }
}

/** The props a nested form's value stands for: described properties only, empty ones omitted, numbers as numbers, recursively structured. */
export function toProps(value: unknown, schema: PropsSchema | PropsSchemaProperty | undefined | null): Record<string, unknown> {
  if (!schema?.properties || typeof value !== 'object' || value === null) {
    return {};
  }
  const props: Record<string, unknown> = {};
  for (const [name, property] of Object.entries(schema.properties)) {
    const raw = (value as Record<string, unknown>)[name];
    if (raw === undefined || raw === null || raw === '') continue;

    const cleaned = cleanPropValue(raw, property);
    if (cleaned !== undefined) {
      props[name] = cleaned;
    }
  }
  return props;
}

function cleanNumericProp(raw: unknown): number | undefined {
  if (typeof raw === 'number') {
    return Number.isNaN(raw) ? undefined : raw;
  }
  if (typeof raw === 'string') {
    const trimmed = raw.trim();
    if (trimmed === '') return undefined;
    const parsed = Number(trimmed);
    return Number.isNaN(parsed) ? undefined : parsed;
  }
  return undefined;
}

function cleanStringProp(raw: unknown): unknown {
  if (typeof raw === 'string') {
    const trimmed = raw.trim();
    return trimmed === '' ? undefined : trimmed;
  }
  return raw;
}

function cleanObjectProp(raw: unknown, property: PropsSchemaProperty): Record<string, unknown> | undefined {
  if (property.properties) {
    const cleanedObj = toProps(raw, property);
    return Object.keys(cleanedObj).length > 0 ? cleanedObj : undefined;
  }
  return typeof raw === 'object' && raw !== null && Object.keys(raw).length > 0 ? (raw as Record<string, unknown>) : undefined;
}

function cleanObjectArrayRows(raw: unknown[], itemSchema: PropsSchemaProperty): Record<string, unknown>[] | undefined {
  const rows: Record<string, unknown>[] = [];
  for (const item of raw) {
    if (typeof item === 'object' && item !== null) {
      const cleanedRow = toProps(item, itemSchema);
      if (Object.keys(cleanedRow).length > 0) {
        rows.push(cleanedRow);
      }
    }
  }
  return rows.length > 0 ? rows : undefined;
}

function cleanStringArray(raw: unknown[]): string[] | undefined {
  const strings = raw.map((s) => (typeof s === 'string' ? s.trim() : String(s))).filter((s) => s.length > 0);
  return strings.length > 0 ? strings : undefined;
}

function cleanArrayProp(raw: unknown, property: PropsSchemaProperty): unknown[] | undefined {
  if (!Array.isArray(raw)) return undefined;
  if (property.items?.type === 'object' && property.items?.properties) {
    return cleanObjectArrayRows(raw, property.items);
  }
  if (property.items?.type === 'string') {
    return cleanStringArray(raw);
  }
  return raw.length > 0 ? raw : undefined;
}

function cleanPropValue(raw: unknown, property: PropsSchemaProperty): unknown {
  if (property.format === ARTIFACT_FORMAT) {
    return raw;
  }
  switch (property.type) {
    case 'number':
    case 'integer':
      return cleanNumericProp(raw);
    case 'string':
      return cleanStringProp(raw);
    case 'boolean':
      return typeof raw === 'boolean' ? raw : undefined;
    case 'object':
      return cleanObjectProp(raw, property);
    case 'array':
      return cleanArrayProp(raw, property);
    default:
      return raw;
  }
}

export function createWidgetPropsAttrDescriptor(attrName = 'props', typeAttrName = 'type', label = 'Props'): WidgetPropsAttrDescriptor {
  return new WidgetPropsAttrDescriptor(attrName, typeAttrName, label);
}
