import { inject } from '@angular/core';
import { Routes } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import {
  CONTEXT_COLLECTION_CONTEXT_KEY,
  CONTEXT_COLLECTION_ENTITY_KEY,
  CONTEXT_COLLECTION_PATH_KEY,
  ContextCollectionComponent,
  entityScreenRoute,
  EntityScreenResolver,
  workingContextRoutes,
} from '@processpuzzle/base-entity';

/** The aggregate root of the demonstration — a metadata entity, seeded by base-entity's `processpuzzle-testbed-entities.yaml`. */
export const DEMO_ROOT_NAME = 'Demo Root';
/** The two kinds of part the root embeds. */
export const DEMO_COMPONENT_ONE_NAME = 'Demo Component One';
export const DEMO_COMPONENT_TWO_NAME = 'Demo Component Two';

/** The working context the demonstration declares: the Demo Root a user has selected. */
export const DEMO_CONTEXT_NAME = 'demo-root';
/** Where the page is mounted; what a selection from the root's own list navigates below. */
export const CONTEXT_OBJECT_BASE_PATH = '/base-entity/context-object';

export const AGGREGATE_ROOT_PATH = 'aggregate-root';
/** The path of a part's list, and the URL segment of its embedded form: the same, so the page's button stays selected while a row is edited. */
export const COMPONENT_ONE_PATH = 'demo-component-one';
export const COMPONENT_TWO_PATH = 'demo-component-two';

/**
 * The screens of the demonstration, contributed as routes at run time because the three entities exist only as metadata:
 *
 * - `aggregate-root` — the root's ordinary list and details. Where the root is selected: its list offers "Set as active".
 * - `ctx/demo-root/<id>/demo-component-one|two` — a list of that part, for the selected root. A row, and Add, open the part's
 *   own form below the root (`…/demo-component-one/<rowId>/details`), saved on its own, without opening the root.
 * - `ctx/demo-root/<part>` with no id — the same view when no root is selected: it asks for one, and continues with the one
 *   chosen last.
 */
export async function contextObjectRoutes(): Promise<Routes> {
  const screens = await inject(EntityScreenResolver).resolve(DEMO_ROOT_NAME);
  return [
    { path: '', pathMatch: 'full', redirectTo: AGGREGATE_ROOT_PATH },
    { path: AGGREGATE_ROOT_PATH, ...entityScreenRoute({ entityName: DEMO_ROOT_NAME, screens, hostPath: AGGREGATE_ROOT_PATH }) },
    ...workingContextRoutes(
      DEMO_CONTEXT_NAME,
      [collectionRoute(COMPONENT_ONE_PATH, 'componentsOne', DEMO_COMPONENT_ONE_NAME), collectionRoute(COMPONENT_TWO_PATH, 'componentsTwo', DEMO_COMPONENT_TWO_NAME)],
      { entityName: DEMO_ROOT_NAME, embeddedChildren: screens?.embeddedChildren ?? [] },
    ),
  ];
}

function collectionRoute(path: string, attribute: string, entityName: string) {
  return {
    path,
    component: ContextCollectionComponent,
    data: { [CONTEXT_COLLECTION_CONTEXT_KEY]: DEMO_CONTEXT_NAME, [CONTEXT_COLLECTION_PATH_KEY]: attribute, [CONTEXT_COLLECTION_ENTITY_KEY]: entityName },
  };
}
