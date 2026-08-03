import { inject, Injectable } from '@angular/core';
import { Route, RouteConfigLoadEnd, Router, Routes } from '@angular/router';

export const ENTITY_NAME_ROUTE_DATA_KEY = 'entityName';

/** Marks a route branch as an embedded child, which is addressed relative to its owner rather than absolutely. */
export const EMBEDDED_ENTITY_ROUTE_DATA_KEY = 'embeddedEntity';

interface MaybeLoaded extends Route {
  _loadedRoutes?: Routes;
}

@Injectable({ providedIn: 'root' })
export class EntityRouteRegistry {
  private readonly router = inject(Router);
  private readonly basePaths = new Map<string, string>();

  scan(): void {
    this.basePaths.clear();
    this.walk(this.router.config, '');
  }

  observeLazyLoads(): void {
    this.router.events.subscribe((event) => {
      if (event instanceof RouteConfigLoadEnd) this.scan();
    });
  }

  basePath(entityName: string): string | undefined {
    return this.basePaths.get(entityName);
  }

  listPath(entityName: string): string | undefined {
    const base = this.basePaths.get(entityName);
    return base ? `${base}/list` : undefined;
  }

  detailsPath(entityName: string, id: string): string | undefined {
    const base = this.basePaths.get(entityName);
    return base ? `${base}/${id}/details` : undefined;
  }

  registeredEntities(): ReadonlyArray<string> {
    return [...this.basePaths.keys()];
  }

  private walk(routes: Routes | undefined, prefix: string): void {
    if (!routes) return;
    for (const route of routes) {
      const segment = this.append(prefix, route.path);
      const entityName = route.data?.[ENTITY_NAME_ROUTE_DATA_KEY];
      // An embedded entity has no single base path — the same child type hangs under every owner that
      // carries it, at whatever depth — so it is addressed relative to the current URL instead. Registering
      // it here would mean the last branch the router happened to expand wins.
      if (typeof entityName === 'string' && entityName.length > 0 && route.data?.[EMBEDDED_ENTITY_ROUTE_DATA_KEY] !== true) {
        this.basePaths.set(entityName, segment);
      }
      this.walk(route.children, segment);
      this.walk((route as MaybeLoaded)._loadedRoutes, segment);
    }
  }

  private append(prefix: string, path: string | undefined): string {
    if (!path) return prefix;
    return `${prefix}/${path}`;
  }
}
