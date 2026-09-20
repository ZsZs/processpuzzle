import { inject, Injectable, Signal, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { DESIGN_ROUTE_PREFIX } from '@processpuzzle/util';
import { filter } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class DesignRouteService {
  private readonly router = inject(Router);
  /**
   * `''` unless the hosting application mounts the designer under a path — see
   * {@link DESIGN_ROUTE_PREFIX}. `processpuzzle-custom-frontend` provides `/{orgKey}`, because one
   * build serves every customer and the organization is a path segment.
   */
  private readonly prefix = inject(DESIGN_ROUTE_PREFIX);
  private readonly _isDesignRoute = signal(this.matchesDesignRoute(this.router.url));
  readonly isDesignRoute: Signal<boolean> = this._isDesignRoute.asReadonly();

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => this._isDesignRoute.set(this.matchesDesignRoute(event.urlAfterRedirects)));
  }

  private matchesDesignRoute(url: string): boolean {
    return url.startsWith(`${this.prefix}/design`);
  }
}
