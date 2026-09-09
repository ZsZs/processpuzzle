import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { STATE_TRANSITION_ACTION_I18N_SCOPE, STATE_TRANSITION_GUARD_I18N_SCOPE } from '../../base-state.i18n';
import {
  STATE_MACHINE_TRANSITION_ENTITY_NAME,
  STATE_TRANSITION_ACTION_ENTITY_NAME,
  STATE_TRANSITION_GUARD_ENTITY_NAME,
} from './state-entity-names';

export { STATE_TRANSITION_ACTION_ENTITY_NAME, STATE_TRANSITION_GUARD_ENTITY_NAME };

/**
 * A `GuardRef` and an `ActionRef` have no `id` — the bean they name is what identifies them. Referencing
 * attributes therefore have to set `referenceIdField = 'beanName'`; see the `guards` and `actions`
 * attributes of the `State Machine Transition` descriptor.
 */
export const BEAN_REF_ID_FIELD = 'beanName';

/**
 * One factory for both, because the contract's two schemas are the same two fields and a guard authored
 * on a transition has to behave exactly like an action authored on it. Only the entity name, the label
 * and the transloco scope differ — which is enough for the two to be separate entities in the registry
 * and so to have separate forms, without a second copy of this layout to keep in step.
 */
function createBeanRefAttrDescriptors(kindLabel: string): AbstractAttrDescriptor[] {
  const beanNameAttr = new BaseEntityAttrDescriptor('beanName', FormControlType.TEXT_BOX, 'Bean Name', undefined, true);
  beanNameAttr.required = true;
  beanNameAttr.isHeading = true;
  beanNameAttr.placeholder = `Spring bean implementing Transition${kindLabel}`;

  // `additionalProperties: true` by contract — static configuration the bean reads alongside the
  // run-time transition context — so an open key/value editor is the only shape that can carry it.
  const paramsAttr = new BaseEntityAttrDescriptor('params', FormControlType.ADDITIONAL_PROPERTIES, 'Params');

  const flexBoxContainer = new FlexboxDescriptor([beanNameAttr, paramsAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createGuardRefDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: STATE_TRANSITION_GUARD_ENTITY_NAME,
    attrDescriptors: createBeanRefAttrDescriptors('Guard'),
    i18nScope: STATE_TRANSITION_GUARD_I18N_SCOPE,
    componentParent: STATE_MACHINE_TRANSITION_ENTITY_NAME,
    isEmbedded: true,
  });
}

export function createActionRefDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: STATE_TRANSITION_ACTION_ENTITY_NAME,
    attrDescriptors: createBeanRefAttrDescriptors('Action'),
    i18nScope: STATE_TRANSITION_ACTION_I18N_SCOPE,
    componentParent: STATE_MACHINE_TRANSITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
