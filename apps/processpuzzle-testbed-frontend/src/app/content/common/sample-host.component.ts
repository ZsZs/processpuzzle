import { Component, inject, input, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { filter, startWith } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';

export interface SampleTab {
  route: string;
  label: string;
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
        <mat-button-toggle-group [name]="groupName()" [value]="selectedButton()" [aria-label]="ariaLabel()">
          @for (tab of tabs(); track tab.route) {
            <mat-button-toggle [routerLink]="tab.route" [value]="tab.route">{{ tab.label }}</mat-button-toggle>
          }
        </mat-button-toggle-group>
      </div>
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
          const segments = new Set(currentUrl.split(/[/?#]/));
          const matched = tabRoutes.find((tab) => segments.has(tab.route));
          this.selectedButton.set(matched ? matched.route : '');
        }
      });
  }
}
