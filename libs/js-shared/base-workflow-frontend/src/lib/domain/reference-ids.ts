/**
 * Flattens a to-many reference list to the plain id array the contract wants.
 *
 * Needed because a `RELATED_ENTITIES` control writes *whole entities* into its form control when the
 * user picks one — see `RelatedEntitiesListComponent.addSelectedEntityFromNavigatorResponse` — while
 * `WorkflowInput.roles` and its siblings are `string[]`. So a list may hold ids, entities, or
 * both at once: ids for everything loaded from the server, entities for everything just selected.
 *
 * Applied on `fromDto` as well as on `toDto`, on purpose. Going in it is a no-op for a well-behaved
 * payload, and that is the point: a backend or fixture that answers with embedded objects — as the
 * pre-catalog contract did — then loads as ids rather than half-flattening on the next save.
 *
 * Same idiom as the testbed's `TestEntityMapper.components`, and the reason it is a function here is
 * that four attributes across two mappers need it.
 */
/**
 * One entry of a to-many reference list, as it may actually be found there.
 *
 * The index signature is not decoration: what the control writes is the *whole* selected entity, so
 * the type has to admit its other fields — a shape narrowed to `{ id }` alone would reject the very
 * value this module exists to handle.
 */
export type EntityReference = string | { id?: string; [additionalField: string]: unknown };

export function toReferenceIds(value: readonly EntityReference[] | undefined): string[] {
  return (value ?? [])
    .map((reference) => (typeof reference === 'string' ? reference : reference?.id))
    .filter((id): id is string => id !== undefined && id !== '');
}
