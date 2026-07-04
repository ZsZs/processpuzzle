import { AbstractAttrDescriptor, FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
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

export class DescriptorBackedFieldMetadataProvider extends RsqlFieldMetadataProvider {
  private readonly fields: RsqlFieldMetadata[];
  private readonly byName: Map<string, RsqlFieldMetadata>;

  constructor(attrDescriptors: AbstractAttrDescriptor[]) {
    super();
    this.fields = attrDescriptors
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

  private toRsqlField(descriptor: AbstractAttrDescriptor): RsqlFieldMetadata {
    const type: RsqlFieldType = CONTROL_TYPE_TO_RSQL_TYPE[descriptor.formControlType] ?? 'string';
    const field: RsqlFieldMetadata = {
      name: descriptor.attrName,
      type,
      operators: RSQL_OPERATORS_BY_TYPE[type],
    };
    if (descriptor instanceof BaseEntityAttrDescriptor) {
      field.label = descriptor.label;
      if (type === 'enum') {
        const selectables = descriptor.getSelectables();
        field.enumValues = selectables?.map((s) => s.key) ?? [];
      }
    }
    return field;
  }
}
