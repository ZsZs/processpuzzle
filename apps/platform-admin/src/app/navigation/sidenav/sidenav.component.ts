import { Component, inject } from '@angular/core';
import { NgClass } from '@angular/common';
import { MatListItem, MatNavList } from '@angular/material/list';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LayoutService } from '@processpuzzle/util';
import { appRoutes } from '../../app.routes';

/**
 * The navigation list: home, then the four screens `PLATFORM_ADMIN_ROUTES` contributes.
 *
 * Renders `t(menuTitle)` as it stands, unlike the testbed's, which trims a fixed 24 characters off
 * the result to strip a "ProcessPuzzle Testbed - " prefix baked into its translation values. The
 * labels in `assets/i18n/*.json` here carry no prefix, so there is nothing to trim — and a
 * character count in a template is one edit away from cutting a translation in half.
 *
 * Nothing is rendered on a handset: at that width the header moves the same routes into a menu.
 */
@Component({
  selector: 'app-sidenav',
  imports: [MatListItem, MatNavList, NgClass, RouterLink, TranslocoDirective],
  template: `
    @if (!layoutService.isSmallDevice()) {
      <ng-container *transloco="let t; prefix: 'navigation'">
        <mat-nav-list>
          @for (item of routes; track item.path) {
            <mat-list-item [routerLink]="item.path" [ngClass]="layoutService.layoutClass()">
              <span matListItemIcon class="material-symbols-outlined">{{ item.data?.['icon'] }}</span>
              <div matListItemTitle>&nbsp;{{ t(item.data?.['menuTitle']) }}</div>
            </mat-list-item>
          }
        </mat-nav-list>
      </ng-container>
    }
  `,
  styleUrl: 'sidenav.component.scss',
})
export class SidenavComponent {
  readonly layoutService = inject(LayoutService);
  readonly routes = appRoutes.filter((route) => route.title !== null && route.title !== undefined);
}
