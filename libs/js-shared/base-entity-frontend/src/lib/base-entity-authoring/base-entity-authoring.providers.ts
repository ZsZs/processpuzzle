import type { Provider } from '@angular/core';
import type { BaseEntityFacadeRegistry } from '../base-entity-facade/base-entity-facade-registry';
import { ENTITY_ATTRIBUTE_ENTITY_NAME, ENTITY_DEFINITION_ENTITY_NAME } from './entity-authoring-names';
import { EntityAttributeFacade, EntityDefinitionFacade } from './entity-definition.facade';

/**
 * The facades of the definition graph, to be spread into the application's `providers`.
 *
 * The embedded one is here for the same reason the routable one is: an embedded entity has a facade like any
 * other — that is what gives it a store — and only its repository differs, reading and writing the
 * `Entity Definition` document rather than an endpoint of its own.
 */
export const BASE_ENTITY_AUTHORING_FACADE_PROVIDERS: Provider[] = [EntityDefinitionFacade, EntityAttributeFacade];

/**
 * The same facades keyed by entity name, to be spread into the application's `BASE_ENTITY_FACADE_REGISTRY`
 * value.
 *
 * Every entity an `EMBEDDED_COMPONENTS` attribute of this branch names has to appear here, or the control
 * throws on first render rather than showing a list whose rows go nowhere on save — the registry is how it
 * reaches the child's store and descriptor. Spread rather than provided separately, because the token holds
 * one value: a second `provide: BASE_ENTITY_FACADE_REGISTRY` would replace the application's own entities
 * instead of adding to them.
 *
 * Note what these two entries do *not* do: they register the screens that author definitions, not the
 * entities a definition declares. Those stay absent from the registry on purpose — a compile-time facade
 * wins over a synthesized one, so registering `Order` here would override the metadata path that
 * `EntityScreenResolver` exists to take.
 */
export const BASE_ENTITY_AUTHORING_ENTITY_FACADES: BaseEntityFacadeRegistry = {
  [ENTITY_DEFINITION_ENTITY_NAME]: EntityDefinitionFacade,
  [ENTITY_ATTRIBUTE_ENTITY_NAME]: EntityAttributeFacade,
};
