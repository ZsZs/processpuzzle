import { FormControlType, type AbstractAttrDescriptor } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor, type Selectable } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { FlexboxDescriptor, FlexDirection } from '../base-entity/flexboxDescriptor';
import { toSelectables } from '../base-entity/selectables';
import { EXCHANGE_RATE_I18N_SCOPE } from '../i18n/base-entity.i18n';
import { EXCHANGE_RATE_ENTITY_NAME } from './entity-authoring-names';

export { EXCHANGE_RATE_ENTITY_NAME };

/** A positive decimal, as the backend reads one: digits with an optional fraction. */
const POSITIVE_DECIMAL = '^(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)$';
/** An ISO date, as the backend reads one. */
const ISO_DATE = '^\\d{4}-\\d{2}-\\d{2}$';

function createExchangeRateAttrDescriptors(currencies: () => Selectable[]): AbstractAttrDescriptor[] {
  // A rate is identified by its pair and date, not by a code: the list's link into the form is the currency converted from.
  const fromAttr = new BaseEntityAttrDescriptor('from', FormControlType.DROPDOWN, 'From', currencies, true);
  fromAttr.required = true;
  fromAttr.isHeading = true;

  const toAttr = new BaseEntityAttrDescriptor('to', FormControlType.DROPDOWN, 'To', currencies);
  toAttr.required = true;

  // How many units of `to` one unit of `from` is worth. The inverse, and a cross rate through another currency, are derived, so
  // a pair is entered once, in whichever direction the organization quotes it.
  const rateAttr = new BaseEntityAttrDescriptor('rate', FormControlType.TEXT_BOX, 'Rate');
  rateAttr.required = true;
  rateAttr.pattern = POSITIVE_DECIMAL;
  rateAttr.placeholder = 'Units of the second currency in one of the first, e.g. 395.5';

  // The first day it holds; it holds until the pair's next rate starts. One rate per pair and date.
  const validFromAttr = new BaseEntityAttrDescriptor('validFrom', FormControlType.TEXT_BOX, 'Valid from');
  validFromAttr.required = true;
  validFromAttr.pattern = ISO_DATE;
  validFromAttr.placeholder = 'YYYY-MM-DD';

  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;
  versionAttr.hideInTable = true;

  const pairRow = new FlexboxDescriptor([fromAttr, toAttr], FlexDirection.ROW);
  pairRow.style = { 'column-gap': '10px' };
  const valueRow = new FlexboxDescriptor([rateAttr, validFromAttr, versionAttr], FlexDirection.ROW);
  valueRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([pairRow, valueRow], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/** @param currencyCodes the currencies of the catalog, read when a dropdown opens — see `UNIT_CATALOG` */
export function createExchangeRateDescriptor(currencyCodes: () => readonly string[]): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: EXCHANGE_RATE_ENTITY_NAME,
    attrDescriptors: createExchangeRateAttrDescriptors(() => toSelectables(currencyCodes())),
    i18nScope: EXCHANGE_RATE_I18N_SCOPE,
  });
}
