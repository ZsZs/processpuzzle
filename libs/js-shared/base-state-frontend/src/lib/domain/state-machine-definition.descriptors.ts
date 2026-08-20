import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { STATE_MACHINE_DEFINITION_I18N_SCOPE } from '../base-state.i18n';
import { STATE_MACHINE_DEFINITION_ENTITY_NAME, STATE_MACHINE_STATE_ENTITY_NAME, STATE_MACHINE_TRANSITION_ENTITY_NAME } from './state-entity-names';
import { STATE_MACHINE_STATE_ID_FIELD } from './state.descriptors';
import { STATE_MACHINE_TRANSITION_ID_FIELD } from './transition.descriptors';

export { STATE_MACHINE_DEFINITION_ENTITY_NAME };

function createStateMachineDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  // The business key *and* the record's identity — see `StateMachineDefinition.id`. `isLinkToDetails`,
  // so the list's own column is what opens the machine, and `isHeading`, so the form and the status bar
  // name the entity type the machine governs.
  const entityNameAttr = new BaseEntityAttrDescriptor('entityName', FormControlType.TEXT_BOX, 'Entity Name', undefined, true);
  entityNameAttr.required = true;
  entityNameAttr.isHeading = true;
  entityNameAttr.placeholder = 'Entity type this machine governs, e.g. order';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // Both are plain text rather than dropdowns, and deliberately so: `stateAttributeKey` names an
  // attribute of *another* entity's definition, which this form has no access to, and `initialStateKey`
  // names a row of the `states` list below — a list being edited in the same form, so a closed list of
  // options would be stale the moment a state is added. The backend validates both on save.
  const stateAttributeKeyAttr = new BaseEntityAttrDescriptor('stateAttributeKey', FormControlType.TEXT_BOX, 'State Attribute');
  stateAttributeKeyAttr.required = true;
  stateAttributeKeyAttr.placeholder = 'TEXT attribute holding the current state, e.g. status';

  const initialStateKeyAttr = new BaseEntityAttrDescriptor('initialStateKey', FormControlType.TEXT_BOX, 'Initial State');
  initialStateKeyAttr.required = true;
  initialStateKeyAttr.placeholder = 'Key of the state a new object starts in';

  // Server-assigned: shown so the author can see which revision is on screen, never edited here.
  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.disabled = true;

  // region nested graph — kept on the entity as-is, so the full-replacement PUT preserves it
  // Containment, not association: the contract nests `states` and `transitions` inside the definition
  // document and gives neither an endpoint of its own, so the rows travel inside this entity's payload
  // and are saved with it.
  const statesAttr = new BaseEntityAttrDescriptor('states', FormControlType.EMBEDDED_COMPONENTS, 'States');
  statesAttr.linkedEntityType = STATE_MACHINE_STATE_ENTITY_NAME;
  // A state has no `id` in the contract; `key` is what identifies it. See STATE_MACHINE_STATE_ID_FIELD.
  statesAttr.referenceIdField = STATE_MACHINE_STATE_ID_FIELD;
  statesAttr.hideInTable = true;

  const transitionsAttr = new BaseEntityAttrDescriptor('transitions', FormControlType.EMBEDDED_COMPONENTS, 'Transitions');
  transitionsAttr.linkedEntityType = STATE_MACHINE_TRANSITION_ENTITY_NAME;
  transitionsAttr.referenceIdField = STATE_MACHINE_TRANSITION_ID_FIELD;
  transitionsAttr.hideInTable = true;
  // endregion

  const identityRow = new FlexboxDescriptor([entityNameAttr, nameAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const bindingRow = new FlexboxDescriptor([stateAttributeKeyAttr, initialStateKeyAttr], FlexDirection.ROW);
  bindingRow.style = { 'column-gap': '10px' };
  const revisionRow = new FlexboxDescriptor([versionAttr, updatedAtAttr], FlexDirection.ROW);
  revisionRow.style = { 'column-gap': '10px' };
  const graphRow = new FlexboxDescriptor([statesAttr, transitionsAttr], FlexDirection.ROW);
  graphRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, bindingRow, revisionRow, descriptionAttr, graphRow], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createStateMachineDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: STATE_MACHINE_DEFINITION_ENTITY_NAME,
    attrDescriptors: createStateMachineDefinitionAttrDescriptors(),
    i18nScope: STATE_MACHINE_DEFINITION_I18N_SCOPE,
  });
}
