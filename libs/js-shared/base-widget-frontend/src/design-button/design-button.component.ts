import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { DESIGN_ROUTE_PREFIX } from '@processpuzzle/util';
import { filter } from 'rxjs';

@Component({
  selector: 'pp-design-button',
  template: `
    <div class="design-button">
      <button mat-icon-button [routerLink]="routerLink()" [attr.aria-label]="ariaLabel()">
        <mat-icon>{{ icon() }}</mat-icon>
      </button>
    </div>
  `,
  styleUrls: ['./design-button.component.css'],
  imports: [MatIcon, MatIconButton, RouterLink],
})
export class DesignButtonComponent {
  private readonly router = inject(Router);
  /**
   * `''` unless the hosting application mounts the designer under a path — see
   * {@link DESIGN_ROUTE_PREFIX}. It prefixes the way *back* as well as the way in: in a shell whose
   * organization is a path segment, `/home` is another tenant's problem and `/{orgKey}/home` is
   * this one's.
   */
  private readonly prefix = inject(DESIGN_ROUTE_PREFIX);
  protected readonly designMode = signal(this.matchesDesignRoute(this.router.url));
  protected readonly icon = computed(() => (this.designMode() ? 'home' : 'design_services'));
  protected readonly routerLink = computed(() => (this.designMode() ? [`${this.prefix}/home`] : [`${this.prefix}/design`]));
  protected readonly ariaLabel = computed(() => (this.designMode() ? 'Home Button' : 'Design Button'));

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => this.designMode.set(this.matchesDesignRoute(event.urlAfterRedirects)));
  }

  /**
   * Compares the URL as the router reads it, not as the address bar shows it: `provideLocaleRouting`
   * serializes every URL with the active language in front (`/en/design`). `parseUrl` runs that
   * serializer, which strips the prefix, and `UrlTree.toString()` re-serializes with Angular's default.
   */
  private matchesDesignRoute(url: string): boolean {
    return this.router.parseUrl(url).toString().startsWith(`${this.prefix}/design`);
  }
}
