import type { Provider } from '@angular/core';
import type { BaseEntityFacadeRegistry } from '@processpuzzle/base-entity';
import { APP_DEFINITION_ENTITY_NAME, APP_NAV_ITEM_ENTITY_NAME, APP_PAGE_ENTITY_NAME, APP_REGION_ENTITY_NAME, APP_WIDGET_ENTITY_NAME } from './domain/app-entity-names';
import { AppDefinitionFacade } from './feature/app-definition.facade';
import { AppNavItemFacade } from './feature/app-nav-item.facade';
import { AppPageFacade } from './feature/app-page.facade';
import { AppRegionFacade } from './feature/app-region.facade';
import { AppWidgetFacade } from './feature/app-widget.facade';

/**
 * The facades of the whole definition graph, to be spread into the application's `providers`.
 *
 * The embedded ones are here for the same reason the routable one is: an embedded entity has a facade
 * like any other — that is what gives it a store — and only its repository differs, reading and writing
 * the `App Definition` document rather than an endpoint of its own.
 */
export const BASE_APP_FACADE_PROVIDERS: Provider[] = [AppDefinitionFacade, AppRegionFacade, AppPageFacade, AppNavItemFacade, AppWidgetFacade];

/**
 * The same facades keyed by entity name, to be spread into the application's
 * `BASE_ENTITY_FACADE_REGISTRY` value.
 *
 * Every entity an `EMBEDDED_COMPONENTS` attribute of this library names has to appear here, or the
 * control throws on first render rather than showing a list whose rows go nowhere on save — the
 * registry is how it reaches the child's store and descriptor. Spread rather than provided separately,
 * because the token holds one value: a second `provide: BASE_ENTITY_FACADE_REGISTRY` would replace the
 * application's own entities instead of adding to them.
 */
export const BASE_APP_ENTITY_FACADES: BaseEntityFacadeRegistry = {
  [APP_DEFINITION_ENTITY_NAME]: AppDefinitionFacade,
  [APP_REGION_ENTITY_NAME]: AppRegionFacade,
  [APP_PAGE_ENTITY_NAME]: AppPageFacade,
  [APP_NAV_ITEM_ENTITY_NAME]: AppNavItemFacade,
  [APP_WIDGET_ENTITY_NAME]: AppWidgetFacade,
};
