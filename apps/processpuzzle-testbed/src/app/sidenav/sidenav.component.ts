import { Component, inject } from '@angular/core';
import { MatActionList, MatListItem } from '@angular/material/list';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIcon } from '@angular/material/icon';
import { LayoutService } from '@processpuzzle/util';
import { NgClass } from '@angular/common';

@Component({
  selector: 'app-sidenav',
  imports: [MatActionList, MatListItem, RouterLinkActive, RouterLink, MatIcon, NgClass],
  template: `
    @if (!layoutService.isSmallDevice()) {
      <mat-action-list>
        <mat-list-item routerLinkActive="active" routerLink="/" [ngClass]="layoutService.layoutClass()">
          <mat-icon matListItemIcon class="material-icons-outlined">home</mat-icon>
          <div matListItemTitle>&nbsp;Home</div>
        </mat-list-item>
        <mat-list-item routerLinkActive="active" routerLink="/util" [ngClass]="layoutService.layoutClass()">
          <span matListItemIcon class="material-symbols-outlined">service_toolbox</span>
          <div matListItemTitle>&nbsp;Utils</div>
        </mat-list-item>
        <mat-list-item routerLinkActive="active" routerLink="/base-forms" [ngClass]="layoutService.layoutClass()">
          <span matListItemIcon class="material-symbols-outlined">checkbook</span>
          <div matListItemTitle>&nbsp;Base Form</div>
        </mat-list-item>
      </mat-action-list>
    }
  `,
  styleUrl: 'sidenav.component.scss',
})
export class SidenavComponent {
  readonly layoutService = inject(LayoutService);
}
