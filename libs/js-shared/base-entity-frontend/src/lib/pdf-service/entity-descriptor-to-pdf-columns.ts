import { AbstractAttrDescriptor, FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
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
 * booleans, dates, tags and artifacts render sensibly without per-field configuration.
 */
export function entityDescriptorToPdfColumns(descriptors: AbstractAttrDescriptor[]): PdfColumnDefinition[] {
  return filterAttributeDescriptors(descriptors)
    .filter((descriptor) => descriptor.hideInTable !== true)
    .map((descriptor) => ({
      field: descriptor.attrName,
      header: descriptor.label,
      align: columnAlign(descriptor.formControlType),
      formatter: columnFormatter(descriptor),
    }));
}

function columnAlign(type: FormControlType): PdfColumnDefinition['align'] {
  switch (type) {
    case FormControlType.CHECKBOX:
      return 'center';
    default:
      return 'left';
  }
}

function columnFormatter(descriptor: BaseEntityAttrDescriptor): PdfColumnDefinition['formatter'] | undefined {
  switch (descriptor.formControlType) {
    case FormControlType.CHECKBOX:
      return (value) => (value === true || value === 'true' ? '✓' : value === false || value === 'false' ? '✗' : '');

    case FormControlType.DATE:
      return (value) => {
        if (!value) return '';
        const date = value instanceof Date ? value : new Date(String(value));
        return isNaN(date.getTime()) ? String(value) : date.toLocaleDateString();
      };

    case FormControlType.TAGS:
      return (value) => (Array.isArray(value) ? value.join(', ') : value == null ? '' : String(value));

    case FormControlType.ARTIFACT:
      return (value) => {
        if (value == null) return '';
        const artifact = value as { name?: string; objectId?: string };
        return artifact.name ?? artifact.objectId ?? '';
      };

    // TEXT_BOX, TEXTAREA, DROPDOWN, FOREIGN_KEY, LOOKUP, etc. → default string coercion in the service
    default:
      return undefined;
  }
}
