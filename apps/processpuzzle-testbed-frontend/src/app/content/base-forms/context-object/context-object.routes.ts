import { inject } from '@angular/core';
import { Routes } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { entityScreenRoute, EntityScreenResolver } from '@processpuzzle/base-entity';

/** The aggregate root of the demonstration — a metadata entity, seeded by base-entity's `processpuzzle-testbed-entities.yaml`. */
export const DEMO_ROOT_NAME = 'Demo Root';
/** The two kinds of part it embeds. */
export const DEMO_COMPONENT_ONE_NAME = 'Demo Component One';
export const DEMO_COMPONENT_TWO_NAME = 'Demo Component Two';

/** The root's URL segment — its name in snake case, which is also the name of the context its `canContextObject` makes. */
export const DEMO_ROOT_PATH = 'demo-root';
/** The URL segment of a part's list: the part's name in snake case, the same segment its embedded form uses. */
export const COMPONENT_ONE_PATH = 'demo-component-one';
export const COMPONENT_TWO_PATH = 'demo-component-two';

/**
 * The one screen the demonstration contributes: the Demo Root's ordinary screens, mounted at `demo-root`. The three entities
 * exist only as metadata, so the route is made at run time. The host route also names the entity, as every mount does, so the navigator builds its URLs on the host's own prefix. Nothing here mentions a context: the root's definition says
 * `canContextObject`, and base-entity adds `demo-root/<id>/<part>/list` for each embedded part by itself.
 */
export async function contextObjectRoutes(): Promise<Routes> {
  const screens = await inject(EntityScreenResolver).resolve(DEMO_ROOT_NAME);
  return [{ path: DEMO_ROOT_PATH, ...entityScreenRoute({ entityName: DEMO_ROOT_NAME, screens, hostPath: DEMO_ROOT_PATH, data: { entityName: DEMO_ROOT_NAME } }) }];
}
