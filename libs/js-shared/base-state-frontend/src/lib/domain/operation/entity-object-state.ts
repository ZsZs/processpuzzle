/**
 * One transition available from an object's current state, as `AvailableTransition` of
 * `base-state-api.yaml` reports it.
 *
 * `guardsSatisfied` is a **dry run**: the server evaluated the transition's guards against the object's
 * payload without firing anything and without running any action. A transition may therefore be listed and
 * still be refused later — the state may have moved on in between — which is why nothing here is cached
 * beyond the render it was fetched for.
 */
export interface AvailableTransition {
  transitionKey: string;
  triggerKey: string;
  targetStateKey: string;
  guardsSatisfied: boolean;
  /** Message of the first guard that rejected. Set only when `guardsSatisfied` is false. */
  blockedReason?: string;
}

/**
 * Where one governed object currently sits in its machine — the operation layer's read side
 * (`GET /entities/{entityName}/{objectId}/state`), as opposed to the knowledge layer's
 * `StateMachineDefinition`.
 *
 * Worth reading through the endpoint rather than off the object's own state attribute, even though that
 * attribute is the only place base-state stores the state: the server falls back to the machine's
 * `initialStateKey` when the attribute is empty, which is exactly the case a seeded object can be in (see
 * `GovernedStateConsistencyCheck`). Reading the attribute directly would show that object as having no
 * state while every transition offered to it starts from the initial one.
 */
export interface EntityObjectState {
  objectId: string;
  /** The machine's key — the entity *definition code* (`order`), not the descriptor name (`Order`). */
  entityName: string;
  currentStateKey: string;
  isFinal: boolean;
  /** When the object last transitioned. Absent until base-state persists a transition log. */
  enteredStateAt?: string;
  availableTransitions: AvailableTransition[];
}
