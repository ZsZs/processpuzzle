import { inject, Injectable, Signal, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class DesignRouteService {
  private readonly router = inject(Router);
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
    return url.startsWith('/design');
  }
}
