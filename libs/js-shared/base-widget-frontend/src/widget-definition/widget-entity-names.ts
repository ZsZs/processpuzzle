/**
 * Entity names of this library's authoring graph, in a file of their own so the descriptors, the routes
 * and the providers can each name them without importing one another — the same device as
 * base-document's `document-entity-names.ts`.
 *
 * A name is what `BASE_ENTITY_FACADE_REGISTRY` keys on and what the route path is snake-cased from, so
 * it is part of the URL: `Widget Definition` mounts at `widget-definition`.
 */
export const WIDGET_DEFINITION_ENTITY_NAME = 'Widget Definition';
export const WIDGET_INPUT_PORT_ENTITY_NAME = 'Widget Input Port';
export const WIDGET_OUTPUT_PORT_ENTITY_NAME = 'Widget Output Port';
