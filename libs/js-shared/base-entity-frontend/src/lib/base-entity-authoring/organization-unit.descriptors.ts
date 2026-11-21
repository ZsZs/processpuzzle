import { FormControlType, type AbstractAttrDescriptor } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { FlexboxDescriptor, FlexDirection } from '../base-entity/flexboxDescriptor';
import { toSelectables } from '../base-entity/selectables';
import { ORGANIZATION_UNIT_I18N_SCOPE } from '../i18n/base-entity.i18n';
import { ORGANIZATION_UNIT_ENTITY_NAME } from './entity-authoring-names';
import { ORGANIZATION_UNIT_STATUSES } from './organization-unit';

export { ORGANIZATION_UNIT_ENTITY_NAME };

/** A positive decimal, as the backend reads one: digits with an optional fraction. */
const POSITIVE_DECIMAL = '^(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)$';

function createOrganizationUnitAttrDescriptors(kinds: readonly string[]): AbstractAttrDescriptor[] {
  // The business key *and* the record's identity — the unit is addressed by its code. Fixed once saved: stored values name it.
  const codeAttr = new BaseEntityAttrDescriptor('code', FormControlType.TEXT_BOX, 'Code', undefined, true);
  codeAttr.required = true;
  codeAttr.isHeading = true;
  codeAttr.createOnly = true;
  codeAttr.placeholder = 'Unique unit code, e.g. pallet';
  codeAttr.pattern = '^[A-Za-z][A-Za-z0-9_-]*$';

  const symbolAttr = new BaseEntityAttrDescriptor('symbol', FormControlType.TEXT_BOX, 'Symbol');
  symbolAttr.required = true;
  symbolAttr.placeholder = 'Shown beside an amount, e.g. plt';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  // What it measures. The kinds are the standard ones — an organization adds units to them, never a kind of its own.
  const kindAttr = new BaseEntityAttrDescriptor('kind', FormControlType.DROPDOWN, 'Kind', toSelectables(kinds));
  kindAttr.required = true;
  kindAttr.createOnly = true;

  // The unit's size in the kind's base unit is factor / divisor: a pallet of 500 kg is factor 500, a gram is 1 / 1000 of a
  // kilogram. Both are fixed once saved, because stored amounts and their base amounts are derived from them.
  const factorAttr = new BaseEntityAttrDescriptor('factor', FormControlType.TEXT_BOX, 'Factor');
  factorAttr.required = true;
  factorAttr.createOnly = true;
  factorAttr.pattern = POSITIVE_DECIMAL;
  factorAttr.placeholder = 'Base units in one of these, e.g. 500';

  const divisorAttr = new BaseEntityAttrDescriptor('divisor', FormControlType.TEXT_BOX, 'Divisor');
  divisorAttr.createOnly = true;
  divisorAttr.pattern = POSITIVE_DECIMAL;
  divisorAttr.placeholder = '1 unless the unit is a fraction';
  divisorAttr.hideInTable = true;

  // The one thing a unit still allows after it is saved: retiring it, which stops it being offered for new values.
  const statusAttr = new BaseEntityAttrDescriptor('status', FormControlType.DROPDOWN, 'Status', toSelectables(ORGANIZATION_UNIT_STATUSES));

  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;
  versionAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([codeAttr, symbolAttr, nameAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const sizeRow = new FlexboxDescriptor([kindAttr, factorAttr, divisorAttr], FlexDirection.ROW);
  sizeRow.style = { 'column-gap': '10px' };
  const lifecycleRow = new FlexboxDescriptor([statusAttr, versionAttr], FlexDirection.ROW);
  lifecycleRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, sizeRow, lifecycleRow], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/** @param kinds the quantity kinds a unit can be added to — the catalog's, see `UNIT_CATALOG` */
export function createOrganizationUnitDescriptor(kinds: readonly string[]): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ORGANIZATION_UNIT_ENTITY_NAME,
    attrDescriptors: createOrganizationUnitAttrDescriptors(kinds),
    i18nScope: ORGANIZATION_UNIT_I18N_SCOPE,
    // A stored value may carry the unit, so it is retired, never deleted: the contract has no DELETE.
    isDeletable: () => false,
  });
}
