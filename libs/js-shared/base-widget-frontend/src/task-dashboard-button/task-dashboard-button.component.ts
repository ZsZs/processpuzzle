import { Component, inject } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TASK_DASHBOARD_ROUTE } from '@processpuzzle/util';

/**
 * The way to the workflow task dashboard — the user's inbox of work — from wherever the application places it.
 *
 * Unlike {@link DesignButtonComponent} it does not toggle: the dashboard is a destination among others, not a
 * mode the whole application switches into, so there is no "way back" to offer. While the dashboard is open
 * the button marks itself active instead, which is what a navigation entry does.
 *
 * Where the dashboard is comes from {@link TASK_DASHBOARD_ROUTE}, which the host provides.
 */
@Component({
  selector: 'pp-task-dashboard-button',
  template: `
    <div class="task-dashboard-button">
      <a mat-icon-button [routerLink]="route" routerLinkActive="task-dashboard-button--active" ariaCurrentWhenActive="page" aria-label="Task Dashboard Button">
        <mat-icon>checklist</mat-icon>
      </a>
    </div>
  `,
  styleUrls: ['./task-dashboard-button.component.css'],
  imports: [MatIcon, MatIconButton, RouterLink, RouterLinkActive],
})
export class TaskDashboardButtonComponent {
  protected readonly route = inject(TASK_DASHBOARD_ROUTE);
}
