import type { Provider } from '@angular/core';
import type { BaseEntityFacadeRegistry } from '@processpuzzle/base-entity';
import {
  STATE_MACHINE_DEFINITION_ENTITY_NAME,
  STATE_MACHINE_STATE_ENTITY_NAME,
  STATE_MACHINE_TRANSITION_ENTITY_NAME,
  STATE_TRANSITION_ACTION_ENTITY_NAME,
  STATE_TRANSITION_GUARD_ENTITY_NAME,
} from './domain/definition/state-entity-names';
import { StateMachineDefinitionFacade } from './feature/definition/state-machine-definition.facade';
import { StateMachineStateFacade, StateMachineTransitionFacade, StateTransitionActionFacade, StateTransitionGuardFacade } from './feature/definition/state-machine-embedded.facades';

/**
 * The facades of the whole state machine graph, to be spread into the application's `providers`.
 *
 * The embedded ones are here for the same reason the routable one is: an embedded entity has a facade like
 * any other — that is what gives it a store — and only its repository differs, reading and writing the
 * `State Machine Definition` document rather than an endpoint of its own.
 */
export const BASE_STATE_FACADE_PROVIDERS: Provider[] = [StateMachineDefinitionFacade, StateMachineStateFacade, StateMachineTransitionFacade, StateTransitionGuardFacade, StateTransitionActionFacade];

/**
 * The same facades keyed by entity name, to be spread into the application's `BASE_ENTITY_FACADE_REGISTRY`
 * value.
 *
 * Every entity an `EMBEDDED_COMPONENTS` attribute of this library names has to appear here, or the control
 * throws on first render rather than showing a list whose rows go nowhere on save — the registry is how it
 * reaches the child's store and descriptor. Spread rather than provided separately, because the token holds
 * one value: a second `provide: BASE_ENTITY_FACADE_REGISTRY` would replace the application's own entities
 * instead of adding to them.
 */
export const BASE_STATE_ENTITY_FACADES: BaseEntityFacadeRegistry = {
  [STATE_MACHINE_DEFINITION_ENTITY_NAME]: StateMachineDefinitionFacade,
  [STATE_MACHINE_STATE_ENTITY_NAME]: StateMachineStateFacade,
  [STATE_MACHINE_TRANSITION_ENTITY_NAME]: StateMachineTransitionFacade,
  [STATE_TRANSITION_GUARD_ENTITY_NAME]: StateTransitionGuardFacade,
  [STATE_TRANSITION_ACTION_ENTITY_NAME]: StateTransitionActionFacade,
};
