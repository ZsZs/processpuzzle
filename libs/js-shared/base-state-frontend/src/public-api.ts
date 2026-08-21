/*
 * Public API Surface of @processpuzzle/base-state
 */

export { BeanRef, State, StateMachineDefinition, Transition, type PropertyMap } from './lib/domain/state-machine-definition';
export {
  BASE_STATE_TRANSLATION_SOURCE,
  BASE_STATE_TRANSLOCO_SCOPE,
  STATE_MACHINE_DEFINITION_I18N_SCOPE,
  STATE_MODELER_I18N_KEY,
  STATE_MACHINE_STATE_I18N_SCOPE,
  STATE_MACHINE_TRANSITION_I18N_SCOPE,
  STATE_TRANSITION_ACTION_I18N_SCOPE,
  STATE_TRANSITION_GUARD_I18N_SCOPE,
} from './lib/base-state.i18n';
export {
  STATE_MACHINE_DEFINITION_ENTITY_NAME,
  STATE_MACHINE_STATE_ENTITY_NAME,
  STATE_MACHINE_TRANSITION_ENTITY_NAME,
  STATE_TRANSITION_ACTION_ENTITY_NAME,
  STATE_TRANSITION_GUARD_ENTITY_NAME,
} from './lib/domain/state-entity-names';
export { createStateMachineDefinitionDescriptor } from './lib/domain/state-machine-definition.descriptors';
export { STATE_MACHINE_STATE_ID_FIELD, createStateDescriptor } from './lib/domain/state.descriptors';
export { STATE_MACHINE_TRANSITION_ID_FIELD, createTransitionDescriptor } from './lib/domain/transition.descriptors';
export { BEAN_REF_ID_FIELD, createActionRefDescriptor, createGuardRefDescriptor } from './lib/domain/bean-ref.descriptors';
export { StateMachineDefinitionMapper } from './lib/domain/state-machine-definition.mapper';
export { StateMachineDefinitionService } from './lib/domain/state-machine-definition.service';
export { StateMachineDefinitionStore } from './lib/domain/state-machine-definition.store';
export { StateMachineDefinitionFacade } from './lib/feature/state-machine-definition.facade';
export { STATE_MODELER_TAB } from './lib/feature/state-modeler-tab';
export { StateModelerTabComponent } from './lib/feature/state-modeler-tab.component';
export { StateMachineStateFacade, StateMachineTransitionFacade, StateTransitionActionFacade, StateTransitionGuardFacade } from './lib/feature/state-machine-embedded.facades';
export { BASE_STATE_ENTITY_FACADES, BASE_STATE_FACADE_PROVIDERS } from './lib/base-state.providers';
export { BASE_STATE_ROUTES } from './lib/base-state.routes';
