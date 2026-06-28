import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
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
  protected readonly designMode = signal(this.matchesDesignRoute(this.router.url));
  protected readonly icon = computed(() => (this.designMode() ? 'home' : 'design_services'));
  protected readonly routerLink = computed(() => (this.designMode() ? ['/home'] : ['/design']));
  protected readonly ariaLabel = computed(() => (this.designMode() ? 'Home Button' : 'Design Button'));

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => this.designMode.set(this.matchesDesignRoute(event.urlAfterRedirects)));
  }

  private matchesDesignRoute(url: string): boolean {
    return url.startsWith('/design');
  }
}
