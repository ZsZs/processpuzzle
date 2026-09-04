import { Component } from '@angular/core';
import { MatCard, MatCardContent, MatCardTitle } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';

/** One card per screen, in the order the sidenav lists them. */
const SECTIONS = [
  { path: '/organization', icon: 'domain', key: 'organizations' },
  { path: '/plan', icon: 'sell', key: 'plans' },
  { path: '/subscription', icon: 'receipt_long', key: 'subscriptions' },
  { path: '/invoice', icon: 'request_quote', key: 'invoices' },
] as const;

/**
 * The landing page. Its route carries the authentication resolver, so reaching it is what sends a
 * staff member to Keycloak — and what makes an unauthenticated visit fail here rather than three
 * clicks later, inside a screen whose list request 401s for no visible reason.
 */
@Component({
  selector: 'app-home',
  imports: [MatCard, MatCardContent, MatCardTitle, RouterLink, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'home'">
      <h1>{{ t('title') }}</h1>
      <p class="subtitle">{{ t('subtitle') }}</p>
      <div class="section-cards">
        @for (section of sections; track section.path) {
          <mat-card class="section-card" [routerLink]="section.path" [attr.test-id]="'home-card-' + section.key">
            <mat-card-title>
              <span class="material-symbols-outlined">{{ section.icon }}</span>
              {{ t(section.key + '.title') }}
            </mat-card-title>
            <mat-card-content>{{ t(section.key + '.text') }}</mat-card-content>
          </mat-card>
        }
      </div>
    </ng-container>
  `,
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  readonly sections = SECTIONS;
}
