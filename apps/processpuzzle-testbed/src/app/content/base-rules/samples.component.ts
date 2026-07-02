import { Component, inject, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { filter, startWith } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'base-rules-samples',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet, MatButtonToggleGroup, MatButtonToggle, MatDivider, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'base-rules'">
      <div style="margin-bottom: 20px">{{ t('samples_desc_1') }}</div>
      <div>
        <strong>{{ t('samples_desc_2') }}</strong>
      </div>
      <div style="margin-top: 20px">
        <mat-button-toggle-group name="ruleSample" [value]="selectedButton()" aria-label="Rule Sample">
          <mat-button-toggle routerLink="order" value="order">Order</mat-button-toggle>
          <mat-button-toggle routerLink="order-line" value="order-line">Order Line</mat-button-toggle>
        </mat-button-toggle-group>
      </div>
      <mat-divider />
      <router-outlet></router-outlet>
    </ng-container>
  `,
})
export class SamplesComponent implements OnInit {
  router = inject(Router);
  selectedButton: WritableSignal<string> = signal('order');

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
          if (currentUrl.includes('order-line')) this.selectedButton.set('order-line');
          else if (currentUrl.includes('order')) this.selectedButton.set('order');
          else this.selectedButton.set('');
        }
      });
  }
}
