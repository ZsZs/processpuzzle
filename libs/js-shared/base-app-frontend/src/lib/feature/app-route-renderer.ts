import { Injectable } from '@angular/core';
import { Route } from '@angular/router';
import { RouteDefinition } from '../domain/app-definition';
import { RouteWidgetsComponent } from './route-widgets.component';
import { RouteEntityComponent } from './route-entity.component';
import { RouteUnsupportedComponent } from './route-unsupported.component';

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
        return { component: RouteWidgetsComponent, title: definition.title, data: { widgets: definition.widgets } };
      case 'ENTITY':
        return {
          component: RouteEntityComponent,
          title: definition.title,
          data: { entityName: definition.entityName, entityMode: definition.entityMode, rsqlFilter: definition.rsqlFilter },
        };
      case 'DOCUMENT':
        return {
          component: RouteUnsupportedComponent,
          title: definition.title,
          data: { reason: `DOCUMENT routes are not yet supported (route '${definition.path}')` },
        };
      default:
        return {
          component: RouteUnsupportedComponent,
          title: definition.title,
          data: { reason: `Unknown route kind '${String(definition.kind)}' (route '${definition.path}')` },
        };
    }
  };
}
