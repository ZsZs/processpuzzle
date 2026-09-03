import { Component, inject } from '@angular/core';
import { NgClass } from '@angular/common';
import { MatListItem, MatNavList } from '@angular/material/list';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LayoutService } from '@processpuzzle/util';
import { navigationItems } from '../navigation-routes';

/**
 * The navigation list: home, then whatever the tenant branch contributed.
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
          @for (item of routes; track item.link) {
            <mat-list-item [routerLink]="item.link" [ngClass]="layoutService.layoutClass()">
              <span matListItemIcon class="material-symbols-outlined">{{ item.icon }}</span>
              <div matListItemTitle>&nbsp;{{ t(item.menuTitle) }}</div>
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
  readonly routes = navigationItems(inject(Router));
}
