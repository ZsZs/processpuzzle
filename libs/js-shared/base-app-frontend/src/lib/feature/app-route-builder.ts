import { EnvironmentProviders, Provider } from '@angular/core';
import { Route, Routes } from '@angular/router';
import { provideTranslocoScope, Translation } from '@jsverse/transloco';
import { ModuleMount, RouteDefinition } from '../domain/app-definition';
import { MODULE_ASSET_CONTEXT, ModuleAssetContext } from '@processpuzzle/widgets';
import { ModuleDefinition, moduleRouteTranslocoId, moduleTranslocoScope } from '../domain/module-definition';
import { BASE_APP_TRANSLOCO_SCOPE, BASE_ENTITY_TRANSLOCO_SCOPE } from '../base-app.i18n';
import { workingContextRoutes } from '@processpuzzle/base-entity';

/** What the builder needs to know of a context: its name, and the one it is nested in. */
export interface ContextRef {
  name: string;
  parent?: string;
}

/** Route data naming the context a route requires; set by {@link emitNode}, read by {@link groupContextRoutes}. */
const REQUIRES_CONTEXT_DATA_KEY = 'requiresContext';

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
 *
 * `loadTranslations` is where a lazily mounted module's own translations come from; without it the module's
 * scope is registered with the application's default loader alone.
 */
export interface AppRouteSource {
  routes?: RouteDefinition[];
  modules?: ModuleMount[];
  /** The app's working contexts. A route naming one is served below `ctx/<name>/:id/`; see {@link groupContextRoutes}. */
  contexts?: ContextRef[];
  moduleRoutes?: Record<string, RouteDefinition[] | undefined>;
  loadModule?: ModuleLoader;
  loadTranslations?: ModuleTranslationLoader;
  /** The assets of a module, by key, for the widgets below its mount; without it a `module:` value resolves to nothing. */
  moduleAssets?: (moduleKey: string) => ModuleAssetContext;
}

/**
 * Fetches the translations of one module and language. `languages` — read when the module loads, so it may be
 * a getter — is what the module's scope is registered
 * for: transloco enumerates an inline loader's languages when the scope is registered, so a loader that
 * answers any language still has to name the ones it answers.
 */
export interface ModuleTranslationLoader {
  readonly languages: string[];
  load(moduleKey: string, scope: string, locale: string): Promise<Translation>;
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
  const appRoutes = await emitLevel(trieOf(source.routes), [], renderRoute, source.contexts ?? []);
  return [...appRoutes, ...(await moduleRoutesOf(source, renderRoute))];
}

async function moduleRoutesOf(source: AppRouteSource, renderRoute: RouteRenderer): Promise<Routes> {
  const mounted: Routes = [];
  for (const mount of source.modules ?? []) {
    if (!mount.basePath) continue;
    const routes = source.moduleRoutes?.[mount.moduleKey];
    if (routes) {
      if (routes.length) mounted.push({ path: mount.basePath, children: await emitLevel(trieOf(routes), [], renderRoute, source.contexts ?? []) });
    } else if (source.loadModule) {
      mounted.push(lazyMount(mount.moduleKey, mount.basePath, source.loadModule, renderRoute, source.loadTranslations, source.moduleAssets, source.contexts ?? []));
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
 * The module's assets reach its widgets the same way, as the {@link MODULE_ASSET_CONTEXT} of the wrapper: a widget
 * property saying `module:docs/welcome.md` is looked up among *this* module's files.
 *
 * The module's own translations — what a user uploaded from the files the Module form prepares — are the scope's
 * *inline loader*, so they load with the module and not before it. Every route title is translated under a key
 * as well, the authored `translocoId` or the one derived from the scope and path (see
 * {@link moduleRouteTranslocoId}), which is what lets an upload translate a title whose designer typed no key.
 *
 * The framework scopes are re-registered alongside it, for the reason `BASE_APP_ROUTES.authoringScopes()`
 * gives: a route that declares TRANSLOCO_SCOPE *replaces* the collection it inherits rather than adding to
 * it, because Angular does not merge `multi` providers across injectors. A module's routes render the same
 * generic screens as everything else — an ENTITY route reaches `BaseEntityTabsComponent`, which translates
 * `base_entity.tabs.*` — so naming only the module's scope here would leave every framework label below the
 * mount unresolved.
 */
function lazyMount(
  moduleKey: string,
  basePath: string,
  loadModule: ModuleLoader,
  renderRoute: RouteRenderer,
  translations?: ModuleTranslationLoader,
  assets?: (moduleKey: string) => ModuleAssetContext,
  contexts: ContextRef[] = [],
): Route {
  return {
    path: basePath,
    loadChildren: async () => {
      const definition = await loadModule(moduleKey);
      if (!definition) return [];
      const scope = moduleTranslocoScope(definition);
      // Every title is translated under a key from here on, authored or derived, so that a module whose
      // designer never typed a `translocoId` can still be translated from the files it downloads.
      const routes = definition.routes?.map((route) => ({ ...route, translocoId: moduleRouteTranslocoId(scope, route) }));
      const children = await emitLevel(trieOf(routes), [], renderRoute, contexts);
      if (!children.length) return [];
      const providers: Array<Provider | EnvironmentProviders> = [
        provideTranslocoScope(
          { scope, alias: scope, ...(translations && { loader: inlineLoaderOf(translations, moduleKey, scope) }) },
          { scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE },
          { scope: BASE_APP_TRANSLOCO_SCOPE, alias: BASE_APP_TRANSLOCO_SCOPE },
        ),
      ];
      // Provided on the wrapper, so every widget below the mount finds the assets of *this* module, and a widget
      // rendered anywhere else finds none.
      if (assets) providers.push({ provide: MODULE_ASSET_CONTEXT, useValue: assets(moduleKey) });
      return [{ path: '', providers, children }];
    },
  };
}

/** One `() => Promise<Translation>` per language, which is the shape transloco's inline loader takes. */
function inlineLoaderOf(translations: ModuleTranslationLoader, moduleKey: string, scope: string): Record<string, () => Promise<Translation>> {
  return Object.fromEntries(translations.languages.map((locale) => [locale, () => translations.load(moduleKey, scope, locale)]));
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
async function emitLevel(nodes: TrieNode[], prefix: string[], renderRoute: RouteRenderer, contexts: ContextRef[]): Promise<Routes> {
  const routes = (await Promise.all(nodes.map((node) => emitNode(node, prefix, renderRoute, contexts)))).flat();
  return groupContextRoutes(
    routes.sort((left, right) => staticDepthOf(right.path) - staticDepthOf(left.path)),
    contexts,
  );
}

/**
 * Moves the routes that require a working context below it. `workingContextRoutes` mounts the views of one
 * context twice — `ctx/<name>/:id/<view>` with the entity resolved, and `ctx/<name>/<view>` as the empty state
 * with the picker — so the authored path of a route becomes its path *inside* the context.
 *
 * A context nested in another (`parent`) is grouped inside its parent's, whose own group is created even when
 * no route names the parent directly: `ctx/race/:id/ctx/heat/:id/scores`. A route whose context the app does
 * not declare is grouped all the same, and shows an empty state with nothing to pick — the backend rejects
 * such a definition, so the only way to see it is one that bypassed validation.
 *
 * Context routes come first: they begin with a static `ctx` segment, and a parameterized sibling must not
 * shadow them.
 */
function groupContextRoutes(routes: Routes, contexts: ContextRef[]): Routes {
  const bound = routes.filter((route) => contextOf(route) !== undefined);
  if (bound.length === 0) return routes;

  const own = new Map<string, Routes>();
  for (const route of bound) own.set(contextOf(route) as string, [...(own.get(contextOf(route) as string) ?? []), route]);

  const parentOf = (name: string) => contexts.find((context) => context.name === name && context.parent && contexts.some((candidate) => candidate.name === context.parent))?.parent;
  const names = new Set(own.keys());
  for (const name of [...names]) for (let parent = parentOf(name); parent && !names.has(parent); parent = parentOf(parent)) names.add(parent);

  const build = (name: string): Routes => workingContextRoutes(name, [...(own.get(name) ?? []), ...[...names].filter((child) => parentOf(child) === name).flatMap(build)]);
  const roots = [...names].filter((name) => parentOf(name) === undefined);
  return [...roots.flatMap(build), ...routes.filter((route) => contextOf(route) === undefined)];
}

function contextOf(route: Route): string | undefined {
  const name = route.data?.[REQUIRES_CONTEXT_DATA_KEY];
  return typeof name === 'string' && name.length > 0 ? name : undefined;
}

async function emitNode(node: TrieNode, prefix: string[], renderRoute: RouteRenderer, contexts: ContextRef[]): Promise<Routes> {
  const path = [...prefix, node.segment];
  if (!node.definition) return (await Promise.all(node.children.map((child) => emitNode(child, path, renderRoute, contexts)))).flat();

  const authoredChildren = await emitLevel(node.children, [], renderRoute, contexts);
  const rendered = await renderRoute(node.definition);
  const route: Route = { ...rendered, path: path.join('/') };
  if (node.definition.requiresContext) route.data = { ...rendered.data, [REQUIRES_CONTEXT_DATA_KEY]: node.definition.requiresContext };
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
