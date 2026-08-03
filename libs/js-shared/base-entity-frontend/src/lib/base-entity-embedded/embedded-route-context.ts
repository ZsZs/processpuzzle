import { ActivatedRouteSnapshot } from '@angular/router';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { ENTITY_NAME_ROUTE_DATA_KEY } from '../base-form-navigator/entity-route.registry';
import { EmbeddedPath, EmbeddedPathStep, indexOfRow, readRows } from './embedded-aggregate';

/** One `<entity>/<id>/details` step of the URL: which entity, and which instance of it. */
export interface EmbeddedRouteLevel {
  entityName: string;
  /** `undefined` on a list route, {@link BaseUrlSegments.NewEntity} for a row being created. */
  entityId: string | undefined;
}

/** A {@link EmbeddedRouteLevel} with the two URLs the level can be navigated by. */
export interface EmbeddedBreadcrumbLevel extends EmbeddedRouteLevel {
  /** This level's own screen — its details form, or its list while no row is named. */
  url: string;
  /** The prefix the level's own `<entity>/...` segments hang off, which a sibling URL is built on. */
  baseUrl: string;
}

/** Everything the store's service needs to read and write one embedded level's rows. */
export interface EmbeddedRouteContext {
  rootEntityName: string;
  rootId: string;
  /** Hops from the root down to the **owner** of these rows; `[]` when the owner is the root itself. */
  path: EmbeddedPath;
  /** The owner's attribute carrying these rows. */
  attrName: string;
  entityName: string;
  referenceIdField?: string;
}

/**
 * The chain of entities the URL walks through, outermost first — `Test Entity/1`, then
 * `Embedded Component/embedded_1_1`, and so on. It reads the URL only, which is what makes a deep link and a
 * page refresh resolve to the same row as a drill-down.
 *
 * The entity name sits on the container route and the id on its `:entityId/details` child, and the router
 * hands both **down** to the routes nested below them. Two rules undo that inheritance:
 *
 * - a name repeated while the level below it still has no id is that level's own details route echoing its
 *   container, not a new level — whereas a repeat *after* an id is a genuinely self-nesting child
 *   (`app-nav-item/a/details/app-nav-item/b/details`), which `App Nav Item` needs;
 * - an id counts only from the route that actually declares `:entityId`, so a nested branch does not adopt
 *   the id of the entity it hangs under.
 */
export function readEmbeddedRouteChain(snapshot: ActivatedRouteSnapshot | null): EmbeddedRouteLevel[] {
  return readEmbeddedBreadcrumb(snapshot).map(({ entityName, entityId }) => ({ entityName, entityId }));
}

/**
 * The same chain, each level carrying the URL of its own screen — which is what turns it into a
 * breadcrumb the user can walk back up.
 *
 * The URL is accumulated from the segments of the routes the walk passes, so a level's `url` ends at the
 * last route that belongs to *it*: `<owner>/<id>/details` for a level a row is named on. `baseUrl` stops
 * one step earlier, before the level's own entity segment, and is what a sibling list or details URL is
 * built on.
 */
export function readEmbeddedBreadcrumb(snapshot: ActivatedRouteSnapshot | null): EmbeddedBreadcrumbLevel[] {
  const snapshots: ActivatedRouteSnapshot[] = [];
  for (let current = snapshot; current; current = current.parent) snapshots.unshift(current);

  const levels: EmbeddedBreadcrumbLevel[] = [];
  let url = '';
  for (const current of snapshots) {
    const entityName = current.data[ENTITY_NAME_ROUTE_DATA_KEY];
    const deepestLevel = levels[levels.length - 1];
    if (typeof entityName === 'string' && entityName.length > 0 && (deepestLevel?.entityName !== entityName || deepestLevel.entityId !== undefined)) {
      levels.push({ entityName, entityId: undefined, url, baseUrl: url });
    }

    url += segmentsOf(current);

    const entityId = current.params[BaseUrlSegments.EntityID];
    if (typeof entityId === 'string' && levels.length > 0 && declaresEntityId(current)) {
      levels[levels.length - 1].entityId = entityId;
    }
    if (levels.length > 0) levels[levels.length - 1].url = url;
  }

  return levels;
}

/** True when `:entityId` is this route's own segment rather than one inherited from an ancestor. */
function declaresEntityId(snapshot: ActivatedRouteSnapshot): boolean {
  return snapshot.routeConfig?.path?.includes(':' + BaseUrlSegments.EntityID) === true;
}

/** This route's own contribution to the URL — empty for a componentless or path-less route. */
function segmentsOf(snapshot: ActivatedRouteSnapshot): string {
  const segments = snapshot.url ?? [];
  return segments.map((segment) => '/' + segment.path).join('');
}

/**
 * Turns the URL chain into a path into the root's payload. Resolving the hops needs the payload itself,
 * because a row's position is what addresses it — the URL carries the row's *key*, and only the aggregate
 * knows where that key currently sits.
 *
 * Returns `undefined` when the chain names no embedded level, when a descriptor is missing, or when an
 * intermediate row cannot be found — a stale deep link, which the caller turns into a redirect rather than
 * into a form bound to the wrong row.
 */
export function resolveEmbeddedRouteContext(levels: readonly EmbeddedRouteLevel[], rootPayload: unknown, descriptorOf: (entityName: string) => BaseEntityDescriptor | undefined): EmbeddedRouteContext | undefined {
  if (levels.length < 2) return undefined;

  const [root, ...embeddedLevels] = levels;
  if (!root.entityId) return undefined;

  const path: EmbeddedPathStep[] = [];
  let ownerEntityName = root.entityName;

  for (const [levelIndex, level] of embeddedLevels.entries()) {
    const ownerDescriptor = descriptorOf(ownerEntityName);
    const attrDescriptor = ownerDescriptor?.embeddedAttrFor(level.entityName);
    if (!attrDescriptor) return undefined;

    const isDeepestLevel = levelIndex === embeddedLevels.length - 1;
    if (isDeepestLevel) {
      return {
        rootEntityName: root.entityName,
        rootId: root.entityId,
        path,
        attrName: attrDescriptor.attrName,
        entityName: level.entityName,
        referenceIdField: attrDescriptor.referenceIdField,
      };
    }

    // An intermediate level is an owner, so its own row has to be located before the walk can go deeper.
    const rows = readRows(rootPayload, path, attrDescriptor.attrName);
    const index = level.entityId === undefined ? -1 : indexOfRow(rows, level.entityId, attrDescriptor.referenceIdField);
    if (index < 0) return undefined;

    path.push({ attrName: attrDescriptor.attrName, index });
    ownerEntityName = level.entityName;
  }

  return undefined;
}
