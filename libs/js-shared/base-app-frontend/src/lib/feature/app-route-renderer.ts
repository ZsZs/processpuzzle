import { inject, Injectable } from '@angular/core';
import { ResolveFn, Route } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { translateLabel } from '@processpuzzle/base-entity';
import { RouteDefinition } from '../domain/app-definition';
import { RouteWidgetsComponent } from './route-widgets.component';
import { RouteEntityComponent } from './route-entity.component';
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
 * `Route` that actually renders it. Owns only `component`/`data`; `path`/`children` are
 * `buildAppRoutes`' to set, per that function's own contract.
 *
 * A separate injectable rather than a free function, even though it's stateless today, because it's
 * the one piece Preview and the eventual production shell must call identically — same reasoning as
 * `buildAppRoutes` itself being free of any component. If this ever needs DI (e.g. a feature flag
 * choosing between two entity-rendering strategies), that's a constructor param away rather than a
 * rewrite.
 *
 * `roles` -> `canMatch` is deliberately not set here yet — see the note on this design's own
 * deferral of that question. `kind: DOCUMENT` is deliberately unhandled too:
 * `BaseDocumentContainerComponent` is an authoring component wired to one document via an injected
 * store, not something parameterizable by a route's `documentSlug` at runtime. Rendering a document
 * read-only inside a live app is its own small design question (reuse a slice of
 * `BaseEntityTabsComponent`'s read path? a dedicated minimal viewer?) and this falls through to
 * {@link RouteUnsupportedComponent} rather than guessing.
 */
@Injectable({ providedIn: 'root' })
export class AppRouteRenderer {
  render = (definition: RouteDefinition): Route => {
    switch (definition.kind) {
      case 'WIDGETS':
        return { component: RouteWidgetsComponent, title: titleOf(definition), data: { widgets: definition.widgets } };
      case 'ENTITY':
        return {
          component: RouteEntityComponent,
          title: titleOf(definition),
          data: { entityName: definition.entityName, entityMode: definition.entityMode, rsqlFilter: definition.rsqlFilter },
        };
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
}
