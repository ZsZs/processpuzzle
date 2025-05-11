import { Component, inject } from '@angular/core';
import { MatListItem, MatNavList } from '@angular/material/list';
import { RouterLink } from '@angular/router';
import { LayoutService, SubstringPipe } from '@processpuzzle/util';
import { NgClass } from '@angular/common';
import { appRoutes } from '../../app.routes';
import { TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'app-sidenav',
  imports: [MatListItem, RouterLink, NgClass, MatNavList, SubstringPipe, TranslocoDirective],
  template: `
    @if (!layoutService.isSmallDevice()) {
      <ng-container *transloco="let t; prefix: 'navigation'">
        <mat-nav-list>
          @for (item of routes; track item) {
            <mat-list-item [routerLink]="item.path" [ngClass]="layoutService.layoutClass()">
              <span matListItemIcon class="material-symbols-outlined">{{ item.data?.['icon'] }}</span>
              <div matListItemTitle>&nbsp;{{ t(item.title | substring: 0) | substring: 24 }}</div>
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
  readonly routes = appRoutes.filter((item) => item.title !== null && item.title !== undefined);
}
