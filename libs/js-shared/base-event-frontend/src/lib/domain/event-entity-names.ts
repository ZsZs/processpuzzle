/**
 * Entity name of the catalog entry. Other features name it as a string — base-workflow's start event points
 * its `eventType` `FOREIGN_KEY` at it — and resolve it through `BASE_ENTITY_FACADE_REGISTRY`, so this value
 * is a cross-feature contract: renaming it silently empties their pickers.
 *
 * The route segment is `snakeCaseName()` of it, `event-definition`.
 */
export const EVENT_DEFINITION_ENTITY_NAME = 'Event Definition';

/**
 * One value an occurrence of an event definition carries — a row of `EventDefinition.parameters`.
 *
 * Embedded in the definition because a parameter means nothing outside it, and an entity of its own because
 * `parameters` is a list the author edits row by row:
 * `event-definition/InvoiceIssued/details/event-parameter/invoiceNumber/details`.
 */
export const EVENT_PARAMETER_ENTITY_NAME = 'Event Parameter';
