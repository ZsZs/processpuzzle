import { inject } from '@angular/core';
import { Routes } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { entityScreenRoute, EntityScreenResolver } from '@processpuzzle/base-entity';

/**
 * The two entities the sample rules in `base-rule-backend`'s `sample-rules/processpuzzle-testbed-rules.yaml`
 * are written against, mounted as the Samples tab of the Base Rule section.
 *
 * Both exist **only as metadata** — `BaseEntityDefinition` rows seeded by `base-entity-backend`'s
 * `default-entities/processpuzzle-testbed-entities.yaml` — so there is no descriptor, facade, store, service
 * or mapper for either in this application. There used to be: seven hand-written files per entity, deleted
 * once the definitions took over. This file replaces all fourteen.
 *
 * What makes the samples samples is that the route above them provides `provideBaseRuleEngine()`. The
 * generated form then loads the rules whose `context` is the descriptor's `entityName` and evaluates them on
 * every change — so `Order` shows the three `Order` rules and `Special Order` shows the one that
 * `extends: positive-quantities` with `override: true`. Nothing here mentions a rule; the binding is the
 * entity name on both sides.
 */

/** Descriptor name of the ordinary order — `BaseEntityDefinition.name` of the `order` definition. */
export const ORDER_NAME = 'Order';

/** Descriptor name of the order that relaxes `positive-quantities`, i.e. the `special-order` definition. */
export const SPECIAL_ORDER_NAME = 'Special Order';

/**
 * URL segments the samples are mounted at. Each **must** equal `snakeCaseName` of its entity name:
 * `BaseFormNavigatorSingletonStore` composes every entity URL from the snake-cased name, so a segment that
 * disagrees leaves the Name column and the tab links navigating to routes that do not match.
 */
export const ORDER_PATH = 'order';
export const SPECIAL_ORDER_PATH = 'special-order';

/** `loadChildren` of the `order` sample. */
export function orderScreenRoutes(): Promise<Routes> {
  return ruleSampleScreenRoutes(ORDER_NAME, ORDER_PATH);
}

/** `loadChildren` of the `special-order` sample. */
export function specialOrderScreenRoutes(): Promise<Routes> {
  return ruleSampleScreenRoutes(SPECIAL_ORDER_NAME, SPECIAL_ORDER_PATH);
}

/**
 * Ask {@link EntityScreenResolver} for the entity by name, hand what it answers to `entityScreenRoute`, and
 * return that as the host route's children — the same recipe as the Dynamic Entity sample under
 * `base-entity`, and for the same reasons.
 *
 * A `loadChildren` rather than a resolver on the component, because the router calls it inside
 * `runInInjectionContext` and waits for the promise before activating a child: `inject()` is legal here, and
 * the outlet exists by the time `order/list` is activated. `inject()` is read before the first `await` on
 * purpose — the ambient injection context is gone afterwards.
 *
 * `hostPath` tells `entityScreenRoute` the entity's own segment is already in the URL, so the generated
 * routes mount path-less rather than producing `order/order/list`.
 *
 * Resolving `Order` resolves its whole aggregate, so the `Order Line` rows on the form are drillable without
 * this file naming the child at all — `DynamicEntityRegistry` follows the `EMBEDDED_COMPONENTS` attribute.
 */
async function ruleSampleScreenRoutes(entityName: string, hostPath: string): Promise<Routes> {
  const screens = await inject(EntityScreenResolver).resolve(entityName);
  return [{ path: '', ...entityScreenRoute({ entityName, screens, hostPath }) }];
}
