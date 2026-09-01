import { FormControlType, type AbstractAttrDescriptor } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { FlexboxDescriptor, FlexDirection } from '../base-entity/flexboxDescriptor';
import { toSelectables } from '../base-entity/selectables';
import { ENTITY_FORM_CONTROL_TYPES, ENTITY_VALUE_KINDS } from '../base-entity-definition/entity-definition';
import { ENTITY_ATTRIBUTE_I18N_SCOPE } from '../i18n/base-entity.i18n';
import { ENTITY_ATTRIBUTE_ENTITY_NAME, ENTITY_DEFINITION_ENTITY_NAME } from './entity-authoring-names';

export { ENTITY_ATTRIBUTE_ENTITY_NAME };

/**
 * An `EntityAttributeDefinition` is identified by `code`, unique within its definition — the contract
 * gives it a read-only uuid nothing addresses it by. Referencing attributes therefore have to set
 * `referenceIdField = 'code'`; see the `attributes` attribute of the `Entity Definition` descriptor.
 */
export const ENTITY_ATTRIBUTE_ID_FIELD = 'code';

function createEntityAttributeAttrDescriptors(): AbstractAttrDescriptor[] {
  const codeAttr = new BaseEntityAttrDescriptor('code', FormControlType.TEXT_BOX, 'Code', undefined, true);
  codeAttr.required = true;
  codeAttr.isHeading = true;
  codeAttr.placeholder = 'Key this attribute is stored under in the payload, e.g. orderNumber';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  // region what the value is, and how it is edited
  // Two fields rather than one, because the contract separates them: `valueKind` decides how the payload
  // is validated and how RSQL casts a comparison, `formControlType` only decides which widget renders it.
  // Both are dropdowns over the *contract's* enums — the backend rejects anything else — and both name
  // more members than this frontend has components for; `controlTypeOf` degrades an unrenderable control
  // to a text box rather than taking the screen down.
  const valueKindAttr = new BaseEntityAttrDescriptor('valueKind', FormControlType.DROPDOWN, 'Value Kind', toSelectables(ENTITY_VALUE_KINDS));
  valueKindAttr.required = true;

  const formControlTypeAttr = new BaseEntityAttrDescriptor('formControlType', FormControlType.DROPDOWN, 'Control Type', toSelectables(ENTITY_FORM_CONTROL_TYPES));
  formControlTypeAttr.required = true;
  // endregion

  // The authored field order, and therefore the form's control order and the table's column order — see
  // `descriptorOf`. A number typed into a text box: `NumberComponent` does not exist, and the contract's
  // `integer` needs nothing more than the numeric keyboard the input type brings.
  const displayOrderAttr = new BaseEntityAttrDescriptor('displayOrder', FormControlType.TEXT_BOX, 'Display Order', undefined, undefined, { inputType: 'number' });

  // region flags
  const requiredAttr = new BaseEntityAttrDescriptor('required', FormControlType.CHECKBOX, 'Required');
  const isMultiValuedAttr = new BaseEntityAttrDescriptor('isMultiValued', FormControlType.CHECKBOX, 'Multi-valued');
  // Worth a column: which attributes are indexed is what decides whether an RSQL range query over this
  // entity is cheap, and reading that off the list beats opening every attribute form.
  const indexedAttr = new BaseEntityAttrDescriptor('indexed', FormControlType.CHECKBOX, 'Indexed');
  // At most one attribute of a definition may set it; the backend is what enforces that, since a form
  // editing one row cannot see its siblings.
  const isLinkToDetailsAttr = new BaseEntityAttrDescriptor('isLinkToDetails', FormControlType.CHECKBOX, 'Titles the record');
  // endregion

  // region conditional fields
  // Each is meaningful only for some `valueKind` / `formControlType` combinations, and all three are
  // shown unconditionally: the form builder has no notion of a control whose visibility depends on
  // another control's value, and the backend ignores a field that does not apply to the kind.
  //
  // `enumValues` is a TAGS control because it is the `ENUM` kind's option list — a free set of short
  // strings, which is exactly what a chip list edits.
  const enumValuesAttr = new BaseEntityAttrDescriptor('enumValues', FormControlType.TAGS, 'Enum Values');
  enumValuesAttr.hideInTable = true;

  // Plain text rather than a dropdown over the tenant's definitions, for the reason the definition's own
  // `componentParents` gives: the definitions are rows of the list this form was opened from, so a closed
  // option list would be stale the moment one is added. The backend resolves the code on save.
  const linkedEntityTypeAttr = new BaseEntityAttrDescriptor('linkedEntityType', FormControlType.TEXT_BOX, 'Linked Entity');
  linkedEntityTypeAttr.placeholder = 'Code of the definition FOREIGN_KEY / EMBEDDED_COMPONENTS points at';
  linkedEntityTypeAttr.hideInTable = true;

  // `defaultValue` is `{}` in the contract — any JSON — but a text box is what a user can type, so a
  // number default round-trips as the string `"5"`. The same narrowing `ADDITIONAL_PROPERTIES` makes, and
  // acceptable for the same reason: every default a seed file has ever carried is a scalar.
  const defaultValueAttr = new BaseEntityAttrDescriptor('defaultValue', FormControlType.TEXT_BOX, 'Default Value');
  defaultValueAttr.hideInTable = true;
  // endregion

  const identityRow = new FlexboxDescriptor([codeAttr, nameAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const kindRow = new FlexboxDescriptor([valueKindAttr, formControlTypeAttr, displayOrderAttr], FlexDirection.ROW);
  kindRow.style = { 'column-gap': '10px' };
  const flagRow = new FlexboxDescriptor([requiredAttr, isMultiValuedAttr, indexedAttr, isLinkToDetailsAttr], FlexDirection.ROW);
  flagRow.style = { 'column-gap': '10px' };
  const referenceRow = new FlexboxDescriptor([linkedEntityTypeAttr, defaultValueAttr], FlexDirection.ROW);
  referenceRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, kindRow, flagRow, referenceRow, enumValuesAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createEntityAttributeDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ENTITY_ATTRIBUTE_ENTITY_NAME,
    attrDescriptors: createEntityAttributeAttrDescriptors(),
    i18nScope: ENTITY_ATTRIBUTE_I18N_SCOPE,
    componentParent: ENTITY_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
