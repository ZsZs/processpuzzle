import { AbstractAttrDescriptor, FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { filterAttributeDescriptors } from '../base-entity/filter-attr-descriptor';
import { RsqlFieldMetadata, RsqlFieldMetadataProvider, RsqlFieldType, RSQL_OPERATORS_BY_TYPE } from './rsql-field-metadata.model';

const SKIP_CONTROL_TYPES: ReadonlySet<FormControlType> = new Set([
  FormControlType.ARTIFACT,
  FormControlType.COMPONENTS,
  FormControlType.FLEX_BOX,
  FormControlType.LABEL,
  FormControlType.TITLE,
]);

const CONTROL_TYPE_TO_RSQL_TYPE: Partial<Record<FormControlType, RsqlFieldType>> = {
  [FormControlType.CHECKBOX]: 'boolean',
  [FormControlType.DATE]: 'date',
  [FormControlType.DROPDOWN]: 'enum',
  [FormControlType.RADIO]: 'enum',
  [FormControlType.FOREIGN_KEY]: 'string',
  [FormControlType.LOOKUP]: 'string',
  [FormControlType.TAGS]: 'string',
  [FormControlType.TEXTAREA]: 'string',
  [FormControlType.TEXT_BOX]: 'string',
};

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
      .map((d) => this.toRsqlField(d));
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
    if (descriptor.formControlType === FormControlType.TEXT_BOX) {
      const inputType = descriptor.options?.inputType;
      if (inputType && INPUT_TYPE_TO_RSQL_TYPE[inputType]) return INPUT_TYPE_TO_RSQL_TYPE[inputType];
    }
    return CONTROL_TYPE_TO_RSQL_TYPE[descriptor.formControlType] ?? 'string';
  }
}
