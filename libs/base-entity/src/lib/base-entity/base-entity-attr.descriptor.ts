import { BaseEntity } from './base-entity';

export enum FormControlType {
  CONTROL_GRID_CONTROL = 'CONTROL_GRID',
  DATE = 'DATE',
  LABEL = 'LABEL',
  TITLE = 'TITLE',
  TEXT_BOX = 'TEXT_BOX',
  DROPDOWN = 'DROPDOWN',
  CHECKBOX = 'CHECKBOX',
  RADIO = 'RADIO',
  TEXTAREA = 'TEXTAREA',
  FOREIGN_KEY = 'FOREIGN_KEY',
}

export class BaseEntityAttrDescriptor<Entity extends BaseEntity> {
  attrName: string;
  description?: string;
  styleClass? = '';
  labelClass?: string = '';
  format?: string;
  isLinkToDetails?: boolean;
  formControlType?: FormControlType;
  selectables?: Array<{ key: string; value: any }>;
  visible = true;
  disabled = false;
  isHeading?: boolean;
  style?: object;
  placeholder?: string;
  lines?: number;
  options: { inputType: 'text' };
  private _label?: string;
  private _linkedEntityType?: any;

  constructor(attrName: string, formControlType?: FormControlType, label?: string, selectables?: Array<{ key: string; value: any }>, isLinkToDetails?: boolean, options?: object) {
    this.attrName = attrName;
    this.formControlType = formControlType;
    this._label = label;
    this.selectables = selectables;
    this.isLinkToDetails = isLinkToDetails;
    this.options = { inputType: 'text', ...options };
  }

  // region properties
  get label(): string {
    return this._label ? this._label : this.attrName;
  }

  set label(label: string) {
    this._label = label;
  }

  get linkedEntityType(): any {
    return this._linkedEntityType;
  }

  setLinkedEntityType(linkedEntityType: { new (): Entity }) {
    this._linkedEntityType = linkedEntityType;
  }

  // endregion
}
