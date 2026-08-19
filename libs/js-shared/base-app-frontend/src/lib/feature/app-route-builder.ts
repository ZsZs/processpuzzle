import { Route, Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ModuleMount, RouteDefinition } from '../domain/app-definition';
import { ModuleDefinition, moduleTranslocoScope } from '../domain/module-definition';
import { BASE_APP_TRANSLOCO_SCOPE, BASE_ENTITY_TRANSLOCO_SCOPE } from '../base-app.i18n';

/**
 * What the run-time shell registers routes from. `routes` and `modules` are the app definition's own
 * fields; the other two are how the routes of a mounted module get in.
 *
 * `moduleRoutes` holds the modules the shell already has, keyed by module key. `loadModule` is for the
 * ones it does not: a mount whose key is absent from `moduleRoutes` becomes a `loadChildren` that calls
 * it on first navigation into the mount. With neither, such a mount contributes nothing — a mount naming
 * a module that has not been authored is a warning rather than an error.
 *
 * A key present in `moduleRoutes` is never loaded again, so an empty array there says "this module has no
 * routes" and is honoured as such.
 */
export interface AppRouteSource {
  routes?: RouteDefinition[];
  modules?: ModuleMount[];
  moduleRoutes?: Record<string, RouteDefinition[] | undefined>;
  loadModule?: ModuleLoader;
}

/**
 * Fetches one module by key, or resolves to `undefined` if the organization has no such module. Rejecting
 * is a navigation error; resolving to `undefined` is the loose coupling the contract asks for.
 */
export type ModuleLoader = (moduleKey: string) => Promise<ModuleDefinition | undefined>;

/**
 * Turns one authored route into the `Route` that renders it — component or `loadComponent`, `title`,
 * `data`, and any `canMatch` guard the definition's `roles` call for. `path` is {@link buildAppRoutes}'
 * to set; `children` it may contribute, and the authored ones are appended to them.
 *
 * May be asynchronous. An `ENTITY` route's screens are built from a `BaseEntityDescriptor` that has to be
 * *fetched* when the entity exists only as metadata, and the routes below it depend on what that
 * descriptor turns out to contain — which embedded children it carries. Making the renderer async is what
 * lets a route be emitted complete or not at all; the alternative, a guard that fills `children` in on the
 * way past, has no way to signal failure other than leaving the route matching nothing.
 */
export type RouteRenderer = (definition: RouteDefinition) => Route | Promise<Route>;

interface TrieNode {
  segment: string;
  definition?: RouteDefinition;
  children: TrieNode[];
}

/**
 * Derives Angular's nested `Routes` from the flat route list of an `AppDefinition`.
 *
 * Nesting is computed here rather than authored, which is the whole point of keeping
 * {@link RouteDefinition} flat: `claims`, `claims/open` and `claims/:id` are three sibling rows a
 * designer can reorder, rename and reason about one at a time, and it is only at registration that
 * they become a parent with two children. Two rules produce that shape:
 *
 * - **An authored route that is a prefix of others becomes their parent.** So `claims` stays mounted
 *   while `claims/open` renders in its `<router-outlet>` — which is the one thing a flat registration
 *   could not express, and the reason this function exists at all. A component rendering a route that
 *   has children therefore has to host an outlet.
 * - **A prefix nothing authored stays part of the path.** With only `claims/open` and `claims/closed`
 *   declared, no synthetic `claims` parent is invented: the two are emitted with their full paths, since
 *   a component-less grouping node would change nothing about what matches or what renders.
 *
 * Modules mount as a parent of their own at {@link ModuleMount.basePath}, component-less because a mount
 * is a prefix rather than a screen. That is the single composition point, and it composes one level: the
 * routes of a mounted module are built by the same two rules, and a module mounts no modules. A module the
 * shell already has is emitted now; one it has not is emitted as `loadChildren` — see {@link lazyMount}.
 *
 * Siblings are ordered static-prefix-first, so `claims/new` is matched before `claims/:id` rather than
 * swallowed by it. Beyond that the authored order is kept, and a duplicate path — which the backend
 * rejects as an error, so it should never arrive — keeps its first occurrence.
 */
export async function buildAppRoutes(source: AppRouteSource, renderRoute: RouteRenderer): Promise<Routes> {
  const appRoutes = await emitLevel(trieOf(source.routes), [], renderRoute);
  return [...appRoutes, ...(await moduleRoutesOf(source, renderRoute))];
}

async function moduleRoutesOf(source: AppRouteSource, renderRoute: RouteRenderer): Promise<Routes> {
  const mounted: Routes = [];
  for (const mount of source.modules ?? []) {
    if (!mount.basePath) continue;
    const routes = source.moduleRoutes?.[mount.moduleKey];
    if (routes) {
      if (routes.length) mounted.push({ path: mount.basePath, children: await emitLevel(trieOf(routes), [], renderRoute) });
    } else if (source.loadModule) {
      mounted.push(lazyMount(mount.moduleKey, mount.basePath, source.loadModule, renderRoute));
    }
  }
  return mounted;
}

/**
 * A mount whose module the shell has not got yet: the same component-less parent at the base path, with
 * its children resolved on first navigation into it instead of now.
 *
 * What is deferred is the *metadata* — one `GET /modules/{key}` per module the user actually visits, so an
 * app that mounts twenty modules does not fetch twenty definitions to show its first screen. It is not a
 * deferred *bundle*: the widgets a module's routes render come from the widget registry and are part of
 * the application's own build either way.
 *
 * The module's transloco scope is registered here, on a component-less wrapper inside the loaded children,
 * because that is the first moment its name is known — the default is the module key, but a module may
 * name its own. The alias is spelled out, as everywhere in this workspace: transloco would otherwise
 * camel-case it and miss every key below it.
 *
 * The framework scopes are re-registered alongside it, for the reason `BASE_APP_ROUTES.authoringScopes()`
 * gives: a route that declares TRANSLOCO_SCOPE *replaces* the collection it inherits rather than adding to
 * it, because Angular does not merge `multi` providers across injectors. A module's routes render the same
 * generic screens as everything else — an ENTITY route reaches `BaseEntityTabsComponent`, which translates
 * `base_entity.tabs.*` — so naming only the module's scope here would leave every framework label below the
 * mount unresolved.
 */
function lazyMount(moduleKey: string, basePath: string, loadModule: ModuleLoader, renderRoute: RouteRenderer): Route {
  return {
    path: basePath,
    loadChildren: async () => {
      const definition = await loadModule(moduleKey);
      const children = await emitLevel(trieOf(definition?.routes), [], renderRoute);
      if (!definition || !children.length) return [];
      const scope = moduleTranslocoScope(definition);
      const providers = [
        provideTranslocoScope(
          { scope, alias: scope },
          { scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE },
          { scope: BASE_APP_TRANSLOCO_SCOPE, alias: BASE_APP_TRANSLOCO_SCOPE },
        ),
      ];
      return [{ path: '', providers, children }];
    },
  };
}

/** The authored paths as a trie, so a route that is a prefix of another can be recognized as its parent. */
function trieOf(routes: RouteDefinition[] | undefined): TrieNode[] {
  const roots: TrieNode[] = [];
  for (const definition of routes ?? []) {
    const segments = segmentsOf(definition.path);
    if (!segments.length) continue;
    let level = roots;
    let node: TrieNode = { segment: '', children: roots };
    for (const segment of segments) {
      const existing = level.find((candidate) => candidate.segment === segment);
      node = existing ?? { segment, children: [] };
      if (!existing) level.push(node);
      level = node.children;
    }
    // First occurrence wins: a duplicate path is a validation error server-side, so the only way to see
    // one here is a definition that bypassed validation, and dropping the later row is the harmless read.
    node.definition ??= definition;
  }
  return roots;
}

/**
 * The routes of one level. An authored node becomes a `Route` whose children are emitted below it; an
 * unauthored one contributes its segment to the paths of its descendants instead of a route of its own.
 */
async function emitLevel(nodes: TrieNode[], prefix: string[], renderRoute: RouteRenderer): Promise<Routes> {
  const routes = (await Promise.all(nodes.map((node) => emitNode(node, prefix, renderRoute)))).flat();
  return routes.sort((left, right) => staticDepthOf(right.path) - staticDepthOf(left.path));
}

async function emitNode(node: TrieNode, prefix: string[], renderRoute: RouteRenderer): Promise<Routes> {
  const path = [...prefix, node.segment];
  if (!node.definition) return (await Promise.all(node.children.map((child) => emitNode(child, path, renderRoute)))).flat();

  const authoredChildren = await emitLevel(node.children, [], renderRoute);
  const rendered = await renderRoute(node.definition);
  const route: Route = { ...rendered, path: path.join('/') };
  // The renderer's own children come first and the authored ones after, rather than the authored ones
  // replacing them. An `ENTITY` route contributes the List and Details routes of its entity *and* may be
  // the path prefix of other authored routes; overwriting would silently drop whichever of the two the
  // builder happened not to know about. The renderer's include an empty-path redirect, which `pathMatch:
  // 'full'` confines to the prefix's own URL, so it cannot shadow an authored sibling.
  const children = [...(rendered.children ?? []), ...authoredChildren];
  if (children.length) route.children = children;
  return [route];
}

function segmentsOf(path: string | undefined): string[] {
  return (path ?? '').split('/').filter((segment) => segment.length > 0);
}

/**
 * How many leading segments of a path are static. Sorting by it descending is what keeps a parameterized
 * route from shadowing its static siblings: `claims/new` scores 2, `claims/:id` scores 1, and Angular
 * matches in the order given.
 */
function staticDepthOf(path: string | undefined): number {
  const segments = segmentsOf(path);
  const firstParameter = segments.findIndex((segment) => segment.startsWith(':') || segment === '**');
  return firstParameter === -1 ? segments.length : firstParameter;
}
