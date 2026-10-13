import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { PropsSchema, PropsSchemaProperty } from './widget-definition';

/**
 * Turns a widget type's `propsSchema` into base-entity form descriptors, so the designer editing a
 * `WidgetInstance` gets a typed control per prop instead of a raw key/value editor.
 *
 * This is the payoff of making `WidgetDefinition` a resource at all: every other contract keeps
 * `props` deliberately loose because each widget type owns its own shape, and `propsSchema` is the
 * one place that shape is finally written down. Reading it here is what turns that declaration into
 * a form, using the same descriptor machinery base-entity already drives entity forms with.
 *
 * ## Deliberately a subset of JSON Schema
 *
 * Only the keywords that map onto a form control are read: `type`, `enum`, `format` (including the
 * {@link ARTIFACT_FORMAT} extension), `maxLength`, `title`, `description`, `required`,
 * nested `type: 'object'` with `properties` (rendered via {@link FormControlType.EMBEDDED_OBJECT}), and
 * `type: 'array'` of objects with `items.type: 'object'` and `items.properties` (rendered via
 * {@link FormControlType.EMBEDDED_LIST}, with `maxItems` mapped to `maxOccurs` and row titles derived from
 * `x-pp-rowLabel` or the first required string property).
 *
 * Everything else — `oneOf`, `$ref`, `patternProperties`, tuple `items` — is *not* interpreted, and the
 * property falls back to {@link FormControlType.ADDITIONAL_PROPERTIES}, the same open editor used
 * when there is no schema at all.
 *
 * That fallback is the important behaviour, not a gap to close later. A schema this function cannot
 * fully read must still yield a usable form, because the alternative — refusing to render, or
 * silently dropping the prop — would make an unrecognised keyword a data-loss bug. Widening the
 * subset is safe precisely because the fallback is never wrong, only less specific.
 *
 * Returns an empty array when there is no schema to read, which the caller distinguishes from
 * "schema describes no props": see {@link hasDescribedProps}.
 */
export function propsSchemaToDescriptors(schema: PropsSchema | undefined | null): AbstractAttrDescriptor[] {
  if (!schema?.properties) {
    return [];
  }
  const required = new Set(schema.required ?? []);
  return Object.entries(schema.properties).map(([name, property]) => toDescriptor(name, property, required.has(name)));
}

/**
 * Whether a schema describes at least one prop. Distinguishes "no schema — props unconstrained,
 * show the open editor" from "a schema that genuinely declares no props", which the contract treats
 * as different states and so must the form.
 */
export function hasDescribedProps(schema: PropsSchema | undefined | null): boolean {
  return Object.keys(schema?.properties ?? {}).length > 0;
}

/** Above this, a string is edited in a textarea rather than a single-line box. */
const TEXTAREA_MIN_LENGTH = 256;

function toDescriptor(name: string, property: PropsSchemaProperty, isRequired: boolean): AbstractAttrDescriptor {
  const cType = controlType(property);
  const descriptor = new BaseEntityAttrDescriptor(name, cType, property.title ?? name, selectables(property), false, inputOptions(property));

  descriptor.required = isRequired;
  if (isArtifactArray(property) || isEmbeddedList(property)) {
    // Several artifacts or list items: unbounded, or capped by `maxItems`.
    descriptor.multiplicity = property.maxItems === undefined ? '0..n' : '0..x';
    descriptor.maxOccurs = property.maxItems;
  }
  if (property.description) {
    descriptor.placeholder = property.description;
  } else if (property.default !== undefined) {
    // No prose to show, so the default is the next most useful hint about what belongs here.
    descriptor.placeholder = `Default: ${JSON.stringify(property.default)}`;
  }

  if (cType === FormControlType.EMBEDDED_OBJECT && property.properties) {
    descriptor.childDescriptors = propsSchemaToDescriptors(property as PropsSchema);
  } else if (cType === FormControlType.EMBEDDED_LIST && property.items?.properties) {
    descriptor.childDescriptors = propsSchemaToDescriptors(property.items as PropsSchema);
    descriptor.rowLabelAttr = resolveRowLabelAttr(property.items);
  }

  return descriptor;
}

function isEmbeddedList(property: PropsSchemaProperty): boolean {
  return property.type === 'array' && property.items?.type === 'object' && !!property.items?.properties;
}

function resolveRowLabelAttr(items: PropsSchemaProperty): string | undefined {
  if (typeof items['x-pp-rowLabel'] === 'string' && (items['x-pp-rowLabel'] as string).trim() !== '') {
    return (items['x-pp-rowLabel'] as string).trim();
  }
  const required = items.required ?? [];
  const firstReqString = required.find((key) => items.properties?.[key]?.type === 'string');
  if (firstReqString) {
    return firstReqString;
  }
  const firstString = Object.entries(items.properties ?? {}).find(([_, prop]) => prop.type === 'string')?.[0];
  return firstString;
}

/**
 * A non-standard JSON Schema `format`: the prop holds a reference to an object-store artifact (an
 * `ArtifactAttr`), edited with the upload/select control rather than as text. JSON Schema leaves `format`
 * open to extension, and a validator that does not know it ignores it.
 */
export const ARTIFACT_FORMAT = 'artifact';

function isArtifactArray(property: PropsSchemaProperty): boolean {
  return property.type === 'array' && property.items?.format === ARTIFACT_FORMAT;
}

function controlType(property: PropsSchemaProperty): FormControlType {
  if (property.format === ARTIFACT_FORMAT || isArtifactArray(property)) {
    return FormControlType.ARTIFACT;
  }
  if (property.enum?.length) {
    return FormControlType.DROPDOWN;
  }
  switch (property.type) {
    case 'string':
      return stringControlType(property);
    case 'number':
    case 'integer':
      return FormControlType.TEXT_BOX;
    case 'boolean':
      return FormControlType.CHECKBOX;
    case 'object':
      return property.properties ? FormControlType.EMBEDDED_OBJECT : FormControlType.ADDITIONAL_PROPERTIES;
    case 'array':
      if (property.items?.type === 'string') {
        return FormControlType.TAGS;
      }
      if (property.items?.type === 'object' && property.items?.properties) {
        return FormControlType.EMBEDDED_LIST;
      }
      return FormControlType.ADDITIONAL_PROPERTIES;
    default:
      return FormControlType.ADDITIONAL_PROPERTIES;
  }
}

function stringControlType(property: PropsSchemaProperty): FormControlType {
  if (property.format === 'date' || property.format === 'date-time') {
    return FormControlType.DATE;
  }
  return (property.maxLength ?? 0) >= TEXTAREA_MIN_LENGTH ? FormControlType.TEXTAREA : FormControlType.TEXT_BOX;
}

function selectables(property: PropsSchemaProperty) {
  return property.enum?.length ? toSelectables(property.enum) : undefined;
}

/**
 * `inputType: 'number'` is what makes the rendered input numeric; `BaseEntityAttrDescriptor`
 * defaults it to `'text'`, so a numeric prop has to say so explicitly.
 */
function inputOptions(property: PropsSchemaProperty): object | undefined {
  return property.type === 'number' || property.type === 'integer' ? { inputType: 'number' } : undefined;
}
