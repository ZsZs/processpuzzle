/**
 * Route data keys of an entity's screens. Kept apart from the screens component, which pulls in the whole entity
 * container, so that the list can read them without importing the screens back.
 */
export const ENTITY_DESCRIPTOR_ROUTE_DATA_KEY = 'entityDescriptor';

export const REQUESTED_ENTITY_ROUTE_DATA_KEY = 'requestedEntityName';

/** The raw RSQL the list of a route is pre-filtered with — `status!=CLOSED`. */
export const RSQL_FILTER_ROUTE_DATA_KEY = 'rsqlFilter';

/** The working context whose active entity the list is filtered by, when its toggle is on. */
export const FILTER_BY_CONTEXT_ROUTE_DATA_KEY = 'filterByContext';
