import { inject } from '@angular/core';
import { Routes } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { entityScreenRoute, EntityScreenResolver } from '@processpuzzle/base-entity';

/**
 * Descriptor name of the sample's entity — the string `base-entity-backend` seeds as
 * `BaseEntityDefinition.name` in `default-entities/processpuzzle-testbed-entities.yaml`. This, and nothing
 * else, is what identifies a dynamic entity: there is no class, no facade and no descriptor for it in this
 * application.
 */
export const DYNAMIC_ENTITY_NAME = 'Dynamic Entity';

/** URL segment the sample is mounted at. Must equal `snakeCaseName(DYNAMIC_ENTITY_NAME)` — see below. */
export const DYNAMIC_ENTITY_PATH = 'dynamic-entity';

/**
 * The whole recipe for mounting an entity that exists only as backend metadata: ask
 * {@link EntityScreenResolver} for it by name, hand what it answers to `entityScreenRoute`, and return that
 * as this route's children.
 *
 * Used as a `loadChildren`, which is what makes the `await` possible — the router calls it inside
 * `runInInjectionContext`, so `inject()` is legal here, and it waits for the promise before activating a
 * child route. Resolving in the container component instead would render a frame with no outlet while
 * `dynamic-entity/list` was already being activated.
 *
 * `hostPath` tells `entityScreenRoute` that the entity's own snake-case segment is already in the URL, so it
 * mounts the generated routes path-less rather than doubling the segment.
 */
export async function dynamicEntityScreenRoutes(): Promise<Routes> {
  const screens = await inject(EntityScreenResolver).resolve(DYNAMIC_ENTITY_NAME);
  return [{ path: '', ...entityScreenRoute({ entityName: DYNAMIC_ENTITY_NAME, screens, hostPath: DYNAMIC_ENTITY_PATH }) }];
}
