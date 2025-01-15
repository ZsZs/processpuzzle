import { Component, inject } from '@angular/core';
import { MatListItem, MatNavList } from '@angular/material/list';
import { RouterLink } from '@angular/router';
import { LayoutService } from '@processpuzzle/util';
import { NgClass } from '@angular/common';
import { appRoutes } from '../app.routes';

@Component({
  selector: 'app-sidenav',
  imports: [MatListItem, RouterLink, NgClass, MatNavList],
  template: `
    @if (!layoutService.isSmallDevice()) {
      <mat-nav-list>
        @for (item of routes; track item) {
          <mat-list-item [routerLink]="item.path" [ngClass]="layoutService.layoutClass()">
            <span matListItemIcon class="material-symbols-outlined">{{ item.data ? item.data['icon'] : '' }}</span>
            <div matListItemTitle>&nbsp;{{ item.title }}</div>
          </mat-list-item>
        }
      </mat-nav-list>
    }
  `,
  styleUrl: 'sidenav.component.scss',
})
export class SidenavComponent {
  readonly layoutService = inject(LayoutService);
  readonly routes = appRoutes.filter((item) => item.title !== null && item.title !== undefined);
}
