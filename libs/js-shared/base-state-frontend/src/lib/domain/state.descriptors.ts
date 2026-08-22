import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { STATE_MACHINE_STATE_I18N_SCOPE } from '../base-state.i18n';
import { STATE_MACHINE_DEFINITION_ENTITY_NAME, STATE_MACHINE_STATE_ENTITY_NAME } from './state-entity-names';

export { STATE_MACHINE_STATE_ENTITY_NAME };

/**
 * A `State` has no `id` — it is identified by the value it writes into the governed entity's state
 * attribute, which is `key`. Referencing attributes therefore have to set `referenceIdField = 'key'`;
 * see the `states` attribute of the `State Machine Definition` descriptor.
 */
export const STATE_MACHINE_STATE_ID_FIELD = 'key';

function createStateAttrDescriptors(): AbstractAttrDescriptor[] {
  const keyAttr = new BaseEntityAttrDescriptor('key', FormControlType.TEXT_BOX, 'Key', undefined, true);
  keyAttr.required = true;
  keyAttr.isHeading = true;
  keyAttr.placeholder = 'Literal value written to the state attribute, e.g. CONFIRMED';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // Both flags are shown in the table: which states are final and which are locked is the whole shape
  // of the machine's boundary, and reading it off the list beats opening five forms.
  const isFinalAttr = new BaseEntityAttrDescriptor('isFinal', FormControlType.CHECKBOX, 'Terminal');
  const isLockedAttr = new BaseEntityAttrDescriptor('isLocked', FormControlType.CHECKBOX, 'Locked');

  // Opaque to the backend — colour, icon, whatever renders the graph — so an open key/value editor is
  // the only shape that can carry it without the form inventing a closed list.
  const metadataAttr = new BaseEntityAttrDescriptor('metadata', FormControlType.ADDITIONAL_PROPERTIES, 'Metadata');
  metadataAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([keyAttr, nameAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const flagRow = new FlexboxDescriptor([isFinalAttr, isLockedAttr], FlexDirection.ROW);
  flagRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, flagRow, descriptionAttr, metadataAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createStateDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: STATE_MACHINE_STATE_ENTITY_NAME,
    attrDescriptors: createStateAttrDescriptors(),
    i18nScope: STATE_MACHINE_STATE_I18N_SCOPE,
    componentParent: STATE_MACHINE_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
