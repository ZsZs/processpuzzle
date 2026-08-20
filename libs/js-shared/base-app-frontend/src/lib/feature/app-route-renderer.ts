import { inject, Injectable } from '@angular/core';
import { ResolveFn, Route } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { entityScreenRoute, EntityScreenResolver, translateLabel } from '@processpuzzle/base-entity';
import { RouteDefinition } from '../domain/app-definition';
import { RouteWidgetsComponent } from './route-widgets.component';
import { RouteUnsupportedComponent } from './route-unsupported.component';

/**
 * The route's `title`, preferring `translocoId` over the authored literal where the key resolves.
 *
 * A `ResolveFn` rather than a plain string because a title is resolved per navigation, which is late
 * enough for a lazily-loaded module scope to have arrived — the string form is captured when the route is
 * built, before the scope that owns the key is registered. `translateLabel` falls back to the literal, so
 * a route whose scope is absent or whose key is missing keeps the authored title.
 */
function titleOf(definition: RouteDefinition): ResolveFn<string> {
  return () => translateLabel(inject(TranslocoService), definition.translocoId, definition.title);
}

/**
 * The `RouteRenderer` {@link buildAppRoutes} needs — turns one authored `RouteDefinition` into the
 * `Route` that actually renders it. Owns `component`, `data` and any `children` the kind calls for; `path`
 * is `buildAppRoutes`' to set, and the routes authored *below* this one are appended to whatever children
 * this contributes.
 *
 * A separate injectable rather than a free function because it is the one piece Preview and the eventual
 * production shell must call identically — same reasoning as `buildAppRoutes` itself being free of any
 * component.
 *
 * `roles` -> `canMatch` is deliberately not set here yet — see the note on this design's own deferral of
 * that question. `kind: DOCUMENT` is deliberately unhandled too: `BaseDocumentContainerComponent` is an
 * authoring component wired to one document via an injected store, not something parameterizable by a
 * route's `documentSlug` at runtime. Rendering a document read-only inside a live app is its own small
 * design question and this falls through to {@link RouteUnsupportedComponent} rather than guessing.
 */
@Injectable({ providedIn: 'root' })
export class AppRouteRenderer {
  private readonly entityScreens = inject(EntityScreenResolver);

  render = async (definition: RouteDefinition): Promise<Route> => {
    switch (definition.kind) {
      case 'WIDGETS':
        return { component: RouteWidgetsComponent, title: titleOf(definition), data: { widgets: definition.widgets } };
      case 'ENTITY':
        return this.entityRoute(definition);
      case 'DOCUMENT':
        return {
          component: RouteUnsupportedComponent,
          title: titleOf(definition),
          data: { reason: `DOCUMENT routes are not yet supported (route '${definition.path}')` },
        };
      default:
        return {
          component: RouteUnsupportedComponent,
          title: titleOf(definition),
          data: { reason: `Unknown route kind '${String(definition.kind)}' (route '${definition.path}')` },
        };
    }
  };

  /**
   * An `ENTITY` route: base-entity's generated screens, mounted at the authored path.
   *
   * Both halves belong to base-entity and are called rather than reimplemented here: `EntityScreenResolver`
   * answers for an entity with a compile-time facade and for one that exists only as a `BaseEntityDefinition`
   * alike, and `entityScreenRoute` owns the URL shape those screens need. What is left is exactly the
   * *application definition* concern — when the routes are built, the authored title, and the authored
   * `entityMode` / `rsqlFilter`.
   *
   * The descriptor is resolved **before** the route exists, which is what makes the route either complete or
   * a leaf. An entity nothing answers to keeps the leaf, whose component says so at the URL the nav item
   * links to; a legitimate state, since an `AppDefinition` may name an entity that has been renamed or one
   * whose definitions this deployment's backend does not serve.
   *
   * `entityMode` is passed along but not applied. A `DETAILS` route lands on the list, from which the row is
   * one click away: making it open a form directly means carrying the authored route's own `:id` into the
   * child URL, and the `RedirectFunction` that would do it receives a *partial* route snapshot whose
   * parameter inheritance at recognition time is not something to bet a navigation on. Deferred with `roles`
   * and `rsqlFilter` rather than guessed at.
   */
  private async entityRoute(definition: RouteDefinition): Promise<Route> {
    const screens = await this.entityScreens.resolve(definition.entityName);
    const route = entityScreenRoute({
      entityName: definition.entityName,
      screens,
      hostPath: definition.path,
      data: { entityMode: definition.entityMode, rsqlFilter: definition.rsqlFilter },
    });

    return { ...route, title: titleOf(definition) };
  }
}
