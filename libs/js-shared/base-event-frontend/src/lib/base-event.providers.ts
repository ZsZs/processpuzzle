import type { Provider } from '@angular/core';
import type { BaseEntityFacadeRegistry } from '@processpuzzle/base-entity';
import { EVENT_DEFINITION_ENTITY_NAME } from './domain/event-definition.descriptors';
import { EventDefinitionFacade } from './feature/event-definition.facade';

/** The facades of the event catalog, to be spread into the application's `providers`. */
export const BASE_EVENT_FACADE_PROVIDERS: Provider[] = [EventDefinitionFacade];

/**
 * The same facades keyed by entity name, to be spread into the application's `BASE_ENTITY_FACADE_REGISTRY`
 * value.
 *
 * This is also what makes the catalog reachable from *other* features: base-workflow's start event names
 * `Event Definition` in a `FOREIGN_KEY` without importing this library, and its picker resolves the store
 * through the registry. Spread rather than provided separately, because the token holds one value.
 */
export const BASE_EVENT_ENTITY_FACADES: BaseEntityFacadeRegistry = {
  [EVENT_DEFINITION_ENTITY_NAME]: EventDefinitionFacade,
};
