import { AbstractAttrDescriptor, FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { effectiveDateFormat, formatDateValue } from '../base-entity/date-format';
import { filterAttributeDescriptors } from '../base-entity/filter-attr-descriptor';
import type { PdfColumnDefinition } from './pdf-export.types';

/**
 * Converts a BaseEntityDescriptor's attribute descriptors into PdfColumnDefinition[]
 * for {@link PdfExportService}.
 *
 * The set of columns mirrors exactly what BaseEntityListComponent renders in its table:
 * descriptors are flattened via {@link filterAttributeDescriptors} (so nested FlexBox
 * attributes are included) and any attribute with `hideInTable === true` is dropped — the
 * same rule the list uses. An attribute hidden from the list is therefore also absent from
 * the PDF.
 *
 * Type-appropriate alignment and value formatters are derived from `formControlType` so
 * booleans, dates, tags and artifacts render sensibly without per-field configuration. A date is written in
 * its attribute's `dateFormat` and `locale` — the active language, as the list shows it — not the browser's.
 */
export function entityDescriptorToPdfColumns(descriptors: AbstractAttrDescriptor[], locale?: string): PdfColumnDefinition[] {
  return filterAttributeDescriptors(descriptors)
    .filter((descriptor) => descriptor.hideInTable !== true)
    .map((descriptor) => ({
      field: descriptor.attrName,
      header: descriptor.label,
      align: columnAlign(descriptor.formControlType),
      formatter: columnFormatter(descriptor, locale),
    }));
}

function columnAlign(type: FormControlType): PdfColumnDefinition['align'] {
  return type === FormControlType.CHECKBOX ? 'center' : 'left';
}

function columnFormatter(descriptor: BaseEntityAttrDescriptor, locale: string | undefined): PdfColumnDefinition['formatter'] | undefined {
  switch (descriptor.formControlType) {
    case FormControlType.CHECKBOX:
      return formatBoolean;

    case FormControlType.DATE:
      return (value) => formatDate(value, descriptor, locale);

    case FormControlType.TAGS:
      return formatTags;

    case FormControlType.ARTIFACT:
      return formatArtifact;

    // TEXT_BOX, TEXTAREA, DROPDOWN, FOREIGN_KEY, LOOKUP, etc. → default string coercion in the service
    default:
      return undefined;
  }
}

function formatBoolean(value: unknown): string {
  if (value === true || value === 'true') return '✓';
  if (value === false || value === 'false') return '✗';
  return '';
}

function formatDate(value: unknown, descriptor: BaseEntityAttrDescriptor, locale: string | undefined): string {
  if (!value) return '';
  return toText(formatDateValue(value, effectiveDateFormat(descriptor), locale ?? navigator.language));
}

function formatTags(value: unknown): string {
  if (Array.isArray(value)) return value.join(', ');
  return toText(value);
}

/** A multi-valued attribute holds an array; its names are joined. */
function formatArtifact(value: unknown): string {
  if (value == null) return '';
  if (Array.isArray(value)) return value.map(formatArtifact).filter(Boolean).join(', ');
  const artifact = value as { name?: string; objectId?: string };
  return artifact.name ?? artifact.objectId ?? '';
}

/** Coerces an arbitrary value to a cell string without ever relying on Object's default `[object Object]` stringification. */
function toText(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'bigint') return value.toString();
  if (typeof value === 'object') return JSON.stringify(value);
  return ''; // symbol / function — not meaningful in a table cell
}
