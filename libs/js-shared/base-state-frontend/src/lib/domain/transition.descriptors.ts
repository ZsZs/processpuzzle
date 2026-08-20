import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { STATE_MACHINE_TRANSITION_I18N_SCOPE } from '../base-state.i18n';
import { BEAN_REF_ID_FIELD } from './bean-ref.descriptors';
import { STATE_MACHINE_DEFINITION_ENTITY_NAME, STATE_MACHINE_TRANSITION_ENTITY_NAME, STATE_TRANSITION_ACTION_ENTITY_NAME, STATE_TRANSITION_GUARD_ENTITY_NAME } from './state-entity-names';

export { STATE_MACHINE_TRANSITION_ENTITY_NAME };

/**
 * A `Transition` has no `id` — `key`, unique within the machine, is what identifies it. Referencing
 * attributes therefore have to set `referenceIdField = 'key'`; see the `transitions` attribute of the
 * `State Machine Definition` descriptor.
 */
export const STATE_MACHINE_TRANSITION_ID_FIELD = 'key';

function createTransitionAttrDescriptors(): AbstractAttrDescriptor[] {
  const keyAttr = new BaseEntityAttrDescriptor('key', FormControlType.TEXT_BOX, 'Key', undefined, true);
  keyAttr.required = true;
  keyAttr.isHeading = true;

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');

  // Plain text, not dropdowns over the machine's states, for the reason given on the definition's
  // `initialStateKey`: the states are rows of a list being edited in the same aggregate, so a closed
  // option list would be stale as soon as one is added. The backend resolves both keys on save.
  const sourceStateKeyAttr = new BaseEntityAttrDescriptor('sourceStateKey', FormControlType.TEXT_BOX, 'Source State');
  sourceStateKeyAttr.required = true;

  const targetStateKeyAttr = new BaseEntityAttrDescriptor('targetStateKey', FormControlType.TEXT_BOX, 'Target State');
  targetStateKeyAttr.required = true;

  const triggerKeyAttr = new BaseEntityAttrDescriptor('triggerKey', FormControlType.TEXT_BOX, 'Trigger');
  triggerKeyAttr.required = true;
  triggerKeyAttr.placeholder = 'Verb callers invoke, e.g. approve';

  // Contained one level deeper than the states and transitions themselves: the contract nests a guard
  // inside the transition that evaluates it, so the rows travel inside the definition's payload too and
  // are addressed through the transition — `state-machine-definition/order/details/
  // state-machine-transition/ship/details/state-transition-guard/…`.
  const guardsAttr = new BaseEntityAttrDescriptor('guards', FormControlType.EMBEDDED_COMPONENTS, 'Guards');
  guardsAttr.linkedEntityType = STATE_TRANSITION_GUARD_ENTITY_NAME;
  guardsAttr.referenceIdField = BEAN_REF_ID_FIELD;
  guardsAttr.hideInTable = true;

  const actionsAttr = new BaseEntityAttrDescriptor('actions', FormControlType.EMBEDDED_COMPONENTS, 'Actions');
  actionsAttr.linkedEntityType = STATE_TRANSITION_ACTION_ENTITY_NAME;
  actionsAttr.referenceIdField = BEAN_REF_ID_FIELD;
  actionsAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([keyAttr, nameAttr, triggerKeyAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const edgeRow = new FlexboxDescriptor([sourceStateKeyAttr, targetStateKeyAttr], FlexDirection.ROW);
  edgeRow.style = { 'column-gap': '10px' };
  const behaviourRow = new FlexboxDescriptor([guardsAttr, actionsAttr], FlexDirection.ROW);
  behaviourRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, edgeRow, behaviourRow], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createTransitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: STATE_MACHINE_TRANSITION_ENTITY_NAME,
    attrDescriptors: createTransitionAttrDescriptors(),
    i18nScope: STATE_MACHINE_TRANSITION_I18N_SCOPE,
    componentParent: STATE_MACHINE_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
