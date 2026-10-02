import type { Type } from '@angular/core';
import { AbstractAttrDescriptor, FormControlType } from './abstact-attr.descriptor';
import type { DateFormat } from './date-format';

export type Selectable = { key: string; value: unknown };
export type SelectablesInput = Array<Selectable> | (() => Array<Selectable>);

export class BaseEntityAttrDescriptor extends AbstractAttrDescriptor {
  description?: string;
  styleClass? = '';
  labelClass?: string = '';
  format?: string;
  /**
   * `DATE` only: the named style the value is shown and edited in, rendered in the active language. Absent
   * styles fall back to the defaults of `effectiveDateFormat`; a `timeStyle` other than `none` adds a time
   * field and makes the value an instant rather than a calendar day.
   */
  dateFormat?: DateFormat;
  isLinkToDetails?: boolean;
  selectables?: SelectablesInput;
  visible = true;
  showThumbnail?: boolean = true;
  hideInTable?: boolean = false;
  /**
   * The list column hugs its content instead of sharing the space left over. Meant for short values — an id,
   * a status, a date. A hugging cell never grows past 60% of the table's width; longer content is cut off
   * with an ellipsis. Columns that are not autosized fill the remaining width and wrap.
   */
  autosizeColumn?: boolean = false;
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
  /**
   * `EMBEDDED_COMPONENTS` only: the rows' order means something — widgets render in it, nav items are listed
   * in it — so the list lets the user reorder them. The order is the array's, so it needs nothing from the
   * backend beyond keeping the array as sent.
   */
  ordered = false;
  /**
   * The control of a {@link FormControlType.CUSTOM} attribute: a `BaseFormControlComponent` subclass the form
   * builder creates like any built-in control. Typed loosely because the base class is generic over the entity
   * and lives in the form layer, which this descriptor must not import.
   */
  component?: Type<unknown>;
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
