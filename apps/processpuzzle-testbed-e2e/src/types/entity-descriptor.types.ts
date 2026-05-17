// src/types/entity-descriptor.types.ts

export type FormControlType =
  | 'TEXT_BOX'
  | 'TEXTAREA'
  | 'FOREIGN_KEY'
  | 'FLEX_BOX'
  | 'CHECKBOX'
  | 'DATE'
  | 'DROPDOWN'
  | 'TAGS'
  | 'ARTIFACT'
  | 'LOOKUP'
  | 'COMPONENTS';

export interface Selectable {
  key: string;
  value: string;
}

export interface AttrDescriptor {
  attrName: string;
  formControlType: FormControlType;
  disabled: boolean;
  label?: string;
  styleClass?: string;
  labelClass?: string;
  isLinkToDetails?: boolean;
  visible?: boolean;
  hideInTable?: boolean;
  required?: boolean;
  options?: { inputType?: string };
  linkedEntityType?: { entityName: string };
  selectables?: Selectable[];
  style?: Record<string, string>;
}

export interface EntityDescriptor {
  entityName: string;
  entityTitle: string;
  attrDescriptors: AttrDescriptor[];
}
