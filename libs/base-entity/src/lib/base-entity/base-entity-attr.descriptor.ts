import { AbstractAttrDescriptor, FormControlType } from './abstact-attr.descriptor';

export class BaseEntityAttrDescriptor extends AbstractAttrDescriptor {
  description?: string;
  styleClass? = '';
  labelClass?: string = '';
  format?: string;
  isLinkToDetails?: boolean;
  selectables?: Array<{ key: string; value: any }>;
  visible = true;
  isHeading?: boolean;
  placeholder?: string;
  lines?: number;
  options: { inputType: 'text' };
  private _label?: string;
  private _linkedEntityType?: string;

  constructor(attrName: string, formControlType: FormControlType, label?: string, selectables?: Array<{ key: string; value: any }>, isLinkToDetails?: boolean, options?: object) {
    super(attrName, formControlType);
    this.attrName = attrName;
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

  set linkedEntityType(linkedEntityType: string) {
    this._linkedEntityType = linkedEntityType;
  }

  // endregion
}
