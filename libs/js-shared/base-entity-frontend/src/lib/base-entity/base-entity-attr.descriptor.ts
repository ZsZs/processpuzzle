import { AbstractAttrDescriptor, FormControlType } from './abstact-attr.descriptor';

export type Selectable = { key: string; value: unknown };
export type SelectablesInput = Array<Selectable> | (() => Array<Selectable>);

export class BaseEntityAttrDescriptor extends AbstractAttrDescriptor {
  description?: string;
  styleClass? = '';
  labelClass?: string = '';
  format?: string;
  isLinkToDetails?: boolean;
  selectables?: SelectablesInput;
  visible = true;
  showThumbnail?: boolean = true;
  hideInTable?: boolean = false;
  isHeading?: boolean;
  placeholder?: string;
  lines?: number;
  options: { inputType: 'text' };
  required = false;
  /**
   * Regular-expression source the value has to match, applied as `Validators.pattern` and anchored by
   * Angular at both ends.
   *
   * For a field the backend constrains beyond "not empty" — a URL slug, a locale tag, an identifier — so the
   * form rejects it where the user typed it rather than letting the save come back a 400 with a message from
   * the server's validation locale. Write the source without delimiters, exactly as the contract's `pattern`
   * gives it: `'^[a-z0-9]+(-[a-z0-9]+)*$'`.
   */
  pattern?: string;
  referenceIdField?: string = 'id';
  private _label?: string;
  private _linkedEntityType?: string;

  constructor(attrName: string, formControlType: FormControlType, label?: string, selectables?: SelectablesInput, isLinkToDetails?: boolean, options?: object) {
    super(attrName, formControlType);
    this._label = label;
    this.selectables = selectables;
    this.isLinkToDetails = isLinkToDetails;
    this.options = { inputType: 'text', ...options };
  }

  getSelectables(): Array<Selectable> | undefined {
    if (this.selectables === undefined) return undefined;
    return typeof this.selectables === 'function' ? this.selectables() : this.selectables;
  }

  // region properties
  get label(): string {
    return this._label ? this._label : this.attrName;
  }

  set label(label: string) {
    this._label = label;
  }

  get linkedEntityType(): string | undefined {
    return this._linkedEntityType;
  }

  set linkedEntityType(linkedEntityType: string | undefined) {
    this._linkedEntityType = linkedEntityType;
  }

  // endregion
}
