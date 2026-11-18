import { Component, computed, inject, input, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { filter, startWith } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';

export interface SampleTab {
  route: string;
  /** Where the button leads, when that is not `route` — which then only names the tab in the URL. */
  link?: string | string[];
  label: string;
  /** Said below the buttons, above the sample, while this tab is the selected one. */
  description?: string;
}

@Component({
  selector: 'pp-sample-host',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet, MatButtonToggleGroup, MatButtonToggle, MatDivider, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: prefix()">
      <div style="margin-bottom: 20px">{{ t(desc1Key()) }}</div>
      <ng-content select="[sample-header]"></ng-content>
      <div>
        <strong>{{ t(desc2Key()) }}</strong>
      </div>
      <div style="margin-top: 20px">
        <mat-button-toggle-group [name]="groupName()" [value]="selectedButton()" [aria-label]="ariaLabel()" [hideSingleSelectionIndicator]="false">
          @for (tab of tabs(); track tab.route) {
            <mat-button-toggle [routerLink]="tab.link ?? tab.route" [value]="tab.route">{{ tab.label }}</mat-button-toggle>
          }
        </mat-button-toggle-group>
      </div>
      @if (selectedDescription(); as description) {
        <p class="sample-description" data-testid="sample-description">{{ description }}</p>
      }
      <ng-content select="[sample-banner]"></ng-content>
      <mat-divider />
      <router-outlet></router-outlet>
    </ng-container>
  `,
})
export class SampleHostComponent implements OnInit {
  private readonly router = inject(Router);

  prefix = input.required<string>();
  groupName = input.required<string>();
  ariaLabel = input<string>('');
  desc1Key = input<string>('samples_desc_1');
  desc2Key = input<string>('samples_desc_2');
  tabs = input.required<SampleTab[]>();

  selectedButton: WritableSignal<string> = signal('');
  selectedDescription = computed(() => this.tabs().find((tab) => tab.route === this.selectedButton())?.description);

  ngOnInit() {
    this.subscribeToRoutingEvents();
  }

  private subscribeToRoutingEvents() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        startWith(this.router),
      )
      .subscribe((event) => {
        const currentUrl: string = event.url;
        if (currentUrl) {
          const tabRoutes = this.tabs();
          const segments = currentUrl.split(/[/?#]/);
          // The tab named deepest in the URL wins: `demo-root/<id>/demo-component-one/list` is the part's tab, not the root's.
          const depthOf = (tab: SampleTab) => segments.lastIndexOf(tab.route);
          const matched = tabRoutes.filter((tab) => depthOf(tab) >= 0).sort((a, b) => depthOf(b) - depthOf(a))[0];
          this.selectedButton.set(matched ? matched.route : '');
        }
      });
  }
}
