import { Component } from '@angular/core';
import { MatActionList, MatListItem } from '@angular/material/list';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'app-sidenav',
  imports: [MatActionList, MatListItem, RouterLinkActive, RouterLink, MatIcon],
  template: `
    <mat-action-list>
      <mat-list-item routerLinkActive="active" routerLink="/">
        <mat-icon matListItemIcon class="material-icons-outlined">home</mat-icon>
        <div matListItemTitle>&nbsp;Home</div>
      </mat-list-item>
      <mat-list-item routerLinkActive="active" routerLink="/base-forms">
        <span matListItemIcon class="material-symbols-outlined">checkbook</span>
        <span matListItemTitle>&nbsp;Basis Formulare</span>
      </mat-list-item>
    </mat-action-list>
  `,
  styles: `
    mat-action-list {
      flex-direction: column;
    }

    mat-list-item {
      flex-direction: row;
      align-items: center;
      justify-content: space-between;
    }
  `,
})
export class SidenavComponent {}
