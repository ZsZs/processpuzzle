import { InjectionToken } from '@angular/core';

/** One transition the entity can take from its current state, as a button or a menu entry shows it. */
export interface EntityStateTransition {
  /** The trigger that fires it — what a route's or widget's `triggers` list names. */
  key: string;
  /** The transition's own name when the machine gives it one, the trigger otherwise. */
  label: string;
  /** False while a guard refuses it; {@link blockedReason} may say why. */
  enabled: boolean;
  blockedReason?: string;
}

/** What is known of an entity's state: where it is, and where it can go. */
export interface EntityStateSnapshot {
  /** The attribute of the entity that holds the state, as the machine names it. */
  stateAttribute?: string;
  currentKey: string;
  /** The current state's name, which is what a badge shows. */
  currentLabel: string;
  transitions: EntityStateTransition[];
}

export interface EntityStateFireResult {
  success: boolean;
  /** Already translated, for the user: the new state, or why the transition was refused. */
  message: string;
}

/**
 * The state machine operations a working context needs, supplied by the state feature (`provideEntityStateControl`
 * in base-state-frontend) rather than called from here, so base-entity does not depend on it. Without a provider
 * a context simply has no state to show and no transitions to offer.
 */
export interface EntityStateActions {
  /** `undefined` for an entity no state machine governs. */
  stateOf(entityName: string, id: string): Promise<EntityStateSnapshot | undefined>;
  fire(entityName: string, id: string, triggerKey: string, version: number): Promise<EntityStateFireResult>;
}

export const ENTITY_STATE_ACTIONS = new InjectionToken<EntityStateActions>('ENTITY_STATE_ACTIONS');
