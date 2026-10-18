import { Component } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { CALCULATION_DESIGNER_TABS } from './calculation-designer.tabs';

/**
 * The Calculations section of the designer: one page whose tabs switch between base-calculation's
 * definitions — with their own Source, Decision tables and Evaluate tabs on the form — and the runs they
 * produce.
 *
 * The same shape as `WorkflowDesignerComponent`, for the same reasons: route buttons rather than a second
 * tab bar above the form's own, staying on screen while the user drills into a definition, and the
 * `design` scope named inline on the directive so nothing here shadows the `base_entity` and
 * `base_calculation` scopes the routes rendered into its outlet register for themselves.
 */
@Component({
  selector: 'pp-calculation-designer',
  standalone: true,
  imports: [MatButton, RouterLink, RouterLinkActive, RouterOutlet, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; scope: 'design'">
      <nav class="designer-nav" aria-label="Calculation designer views">
        @for (tab of tabs; track tab.path) {
          <a
            mat-stroked-button
            class="designer-nav-button"
            [routerLink]="tab.path"
            routerLinkActive="designer-nav-button-active"
            ariaCurrentWhenActive="page"
          >
            <span class="material-symbols-outlined tab-icon">{{ tab.icon }}</span>
            {{ t(tab.label) }}
          </a>
        }
      </nav>
      <router-outlet></router-outlet>
    </ng-container>
  `,
  styles: `
    .designer-nav {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 16px;
    }

    .designer-nav-button {
      border-radius: 999px;
    }

    .designer-nav-button-active {
      background-color: rgb(0 0 0 / 4%);
      border-color: currentcolor;
    }

    .tab-icon {
      margin-right: 6px;
    }
  `,
})
export class CalculationDesignerComponent {
  readonly tabs = CALCULATION_DESIGNER_TABS;
}
