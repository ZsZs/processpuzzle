import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabLink, MatTabNav, MatTabNavPanel } from '@angular/material/tabs';
import { TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'base-starter',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet, MatTabNav, MatTabNavPanel, MatTabLink, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'base-starter'">
      <nav mat-tab-nav-bar [tabPanel]="tabPanel">
        <a mat-tab-link routerLink="overview" routerLinkActive #rla1="routerLinkActive" [active]="rla1.isActive">
          {{ t('overview_tab_label') }}
        </a>
        <a mat-tab-link routerLink="starters" routerLinkActive #rla2="routerLinkActive" [active]="rla2.isActive">
          {{ t('import_tab_label') }}
        </a>
      </nav>
      <mat-tab-nav-panel #tabPanel>
        <router-outlet></router-outlet>
      </mat-tab-nav-panel>
    </ng-container>
  `,
  styles: ``,
})
export class BaseStarterComponent {}
