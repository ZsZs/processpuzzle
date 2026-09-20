import { Component, inject } from '@angular/core';
import { MatListItem, MatNavList } from '@angular/material/list';
import { RouterLink } from '@angular/router';
import { DESIGN_ROUTE_PREFIX, LayoutService } from '@processpuzzle/util';
import { NgClass } from '@angular/common';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { DESIGN_ROUTES } from '../design.routes';

@Component({
  selector: 'pp-design-sidenav',
  standalone: true,
  imports: [MatListItem, RouterLink, NgClass, MatNavList, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'design' })],
  template: `
    @if (!layoutService.isSmallDevice()) {
      <ng-container *transloco="let t">
        <mat-nav-list>
          @for (item of routes; track item) {
            <mat-list-item [routerLink]="prefix + '/design/' + item.path" [ngClass]="layoutService.layoutClass()">
              <span matListItemIcon class="material-symbols-outlined">{{ item.data?.['icon'] }}</span>
              <div matListItemTitle>&nbsp;{{ t(item.data?.['menuTitle']) }}</div>
            </mat-list-item>
          }
        </mat-nav-list>
      </ng-container>
    }
  `,
  styleUrl: 'design-sidenav.component.scss',
})
export class DesignSidenavComponent {
  readonly layoutService = inject(LayoutService);
  /**
   * `''` unless the hosting application mounts the designer under a path — see
   * {@link DESIGN_ROUTE_PREFIX}.
   */
  readonly prefix = inject(DESIGN_ROUTE_PREFIX);
  /**
   * The sections of the designer: the top-level routes that declare a `menuTitle`, which is what this list
   * renders. Filtering on that rather than on `title` — as this did — says what the filter means: a route's
   * `title` is the browser's, and the deepest one wins, so the parent titles here are never displayed.
   * Children are not walked: the Application section's three entities are tabs of its own page.
   */
  readonly routes = DESIGN_ROUTES.filter((item) => item.data?.['menuTitle'] !== undefined);
}
