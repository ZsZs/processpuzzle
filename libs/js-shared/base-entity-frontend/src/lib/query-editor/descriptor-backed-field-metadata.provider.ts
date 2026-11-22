import { STANDARD_UNIT_CATALOG } from '@processpuzzle/util';
import { AbstractAttrDescriptor, FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { filterAttributeDescriptors } from '../base-entity/filter-attr-descriptor';
import { RsqlFieldMetadata, RsqlFieldMetadataProvider, RsqlFieldType, RSQL_OPERATORS_BY_TYPE } from './rsql-field-metadata.model';

const SKIP_CONTROL_TYPES: ReadonlySet<FormControlType> = new Set([
  FormControlType.ADDRESS,
  FormControlType.ARTIFACT,
  FormControlType.AVATAR,
  FormControlType.COMMENTS,
  FormControlType.GEO_LOCATION,
  FormControlType.COMPONENTS,
  FormControlType.CUSTOM,
  FormControlType.EMBEDDED_COMPONENTS,
  FormControlType.FLEX_BOX,
  FormControlType.LABEL,
  FormControlType.RELATED_ENTITIES,
  FormControlType.TITLE,
]);

const CONTROL_TYPE_TO_RSQL_TYPE: Partial<Record<FormControlType, RsqlFieldType>> = {
  [FormControlType.CHECKBOX]: 'boolean',
  [FormControlType.DATE]: 'date',
  [FormControlType.DROPDOWN]: 'enum',
  [FormControlType.RADIO]: 'enum',
  [FormControlType.STATE]: 'string',
  [FormControlType.FOREIGN_KEY]: 'string',
  [FormControlType.LOOKUP]: 'string',
  [FormControlType.TAGS]: 'string',
  [FormControlType.TEXTAREA]: 'string',
  [FormControlType.TEXT_BOX]: 'string',
};

const MEASURE_CONTROL_TYPES: ReadonlySet<FormControlType> = new Set([FormControlType.QUANTITY, FormControlType.MONEY, FormControlType.DIMENSIONS]);

/**
 * A measure is searched through the numbers the backend derives from it, never as the object: a quantity or an amount of
 * money through `baseAmount` (a quantity in its kind's base unit, so `weight.baseAmount=gt=5` finds every weight over 5 kg
 * whatever unit it was entered in), dimensions through `baseArea` and, with three axes, `baseVolume` (m², m³).
 */
function measureFields(descriptor: BaseEntityAttrDescriptor): RsqlFieldMetadata[] {
  const field = (suffix: string, label: string): RsqlFieldMetadata => ({
    name: `${descriptor.attrName}.${suffix}`,
    type: 'number',
    operators: RSQL_OPERATORS_BY_TYPE['number'],
    label,
  });
  switch (descriptor.formControlType) {
    case FormControlType.DIMENSIONS:
      return [field('baseArea', `${descriptor.label} area (m²)`), ...(descriptor.dimensionCount === 3 ? [field('baseVolume', `${descriptor.label} volume (m³)`)] : [])];
    case FormControlType.MONEY:
      return [field('baseAmount', descriptor.label)];
    default: {
      const kind = descriptor.quantityKind ? STANDARD_UNIT_CATALOG.kinds().find((candidate) => candidate.code === descriptor.quantityKind) : undefined;
      return [field('baseAmount', `${descriptor.label} (${kind?.baseUnit ?? 'base unit'})`)];
    }
  }
}

const INPUT_TYPE_TO_RSQL_TYPE: Record<string, RsqlFieldType> = {
  number: 'number',
  date: 'date',
  datetime: 'datetime',
  'datetime-local': 'datetime',
};

export class DescriptorBackedFieldMetadataProvider extends RsqlFieldMetadataProvider {
  private readonly fields: RsqlFieldMetadata[];
  private readonly byName: Map<string, RsqlFieldMetadata>;

  constructor(attrDescriptors: AbstractAttrDescriptor[]) {
    super();
    this.fields = filterAttributeDescriptors(attrDescriptors)
      .filter((d) => !SKIP_CONTROL_TYPES.has(d.formControlType))
      .flatMap((d) => (MEASURE_CONTROL_TYPES.has(d.formControlType) ? measureFields(d) : [this.toRsqlField(d)]));
    this.byName = new Map(this.fields.map((f) => [f.name, f]));
  }

  getFields(): RsqlFieldMetadata[] {
    return this.fields;
  }

  getField(name: string): RsqlFieldMetadata | undefined {
    return this.byName.get(name);
  }

  private toRsqlField(descriptor: BaseEntityAttrDescriptor): RsqlFieldMetadata {
    const type = this.resolveType(descriptor);
    const field: RsqlFieldMetadata = {
      name: descriptor.attrName,
      type,
      operators: RSQL_OPERATORS_BY_TYPE[type],
      label: descriptor.label,
    };
    if (type === 'enum') {
      const selectables = descriptor.getSelectables();
      field.enumValues = selectables?.map((s) => s.key) ?? [];
    }
    return field;
  }

  private resolveType(descriptor: BaseEntityAttrDescriptor): RsqlFieldType {
    if (descriptor.formControlType === FormControlType.TEXT_BOX || descriptor.formControlType === FormControlType.CALCULATED) {
      const inputType = descriptor.options?.inputType;
      if (inputType && INPUT_TYPE_TO_RSQL_TYPE[inputType]) return INPUT_TYPE_TO_RSQL_TYPE[inputType];
    }
    return CONTROL_TYPE_TO_RSQL_TYPE[descriptor.formControlType] ?? 'string';
  }
}
