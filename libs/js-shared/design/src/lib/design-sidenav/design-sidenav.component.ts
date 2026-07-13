import { Component, inject } from '@angular/core';
import { MatListItem, MatNavList } from '@angular/material/list';
import { RouterLink } from '@angular/router';
import { LayoutService } from '@processpuzzle/util';
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
            <mat-list-item [routerLink]="'/design/' + item.path" [ngClass]="layoutService.layoutClass()">
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
  readonly routes = DESIGN_ROUTES.filter((item) => item.title !== null && item.title !== undefined);
}
