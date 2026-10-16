import type { Provider } from '@angular/core';
import type { BaseEntityFacadeRegistry } from '@processpuzzle/base-entity';
import { EVENT_DEFINITION_ENTITY_NAME, EVENT_PARAMETER_ENTITY_NAME } from './domain/event-entity-names';
import { EventDefinitionFacade } from './feature/event-definition.facade';
import { EventParameterFacade } from './feature/event-parameter.facade';

/**
 * The facades of the event catalog, to be spread into the application's `providers`: the definition and the
 * parameters embedded in it. The embedded one is here because an embedded entity has a facade like any
 * other — that is what gives it a store — and only its repository differs.
 */
export const BASE_EVENT_FACADE_PROVIDERS: Provider[] = [EventDefinitionFacade, EventParameterFacade];

/**
 * The same facades keyed by entity name, to be spread into the application's `BASE_ENTITY_FACADE_REGISTRY`
 * value.
 *
 * This is also what makes the catalog reachable from *other* features: base-workflow's start event names
 * `Event Definition` in a `FOREIGN_KEY` without importing this library, and its picker resolves the store
 * through the registry. Spread rather than provided separately, because the token holds one value.
 *
 * The `parameters` `EMBEDDED_COMPONENTS` control resolves its child through the same map, so
 * `Event Parameter` has to be in it or the definition's form throws on first render.
 */
export const BASE_EVENT_ENTITY_FACADES: BaseEntityFacadeRegistry = {
  [EVENT_DEFINITION_ENTITY_NAME]: EventDefinitionFacade,
  [EVENT_PARAMETER_ENTITY_NAME]: EventParameterFacade,
};
