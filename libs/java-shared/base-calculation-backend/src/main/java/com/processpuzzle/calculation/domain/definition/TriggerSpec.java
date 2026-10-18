package com.processpuzzle.calculation.domain.definition;

/**
 * What starts a run. {@code onChange} recomputes whenever an object of the entity is created or updated,
 * {@code state} when one enters a state, and a {@code manual} calculation runs on request only — which every
 * calculation also does. The changed object is bound to the parameter of its entity.
 */
public record TriggerSpec(EntityRef onChange, StateRef state, Boolean manual, FreezeSpec freeze) {
}
