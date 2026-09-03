import { Component, inject } from '@angular/core';
import { MatCard, MatCardContent, MatCardTitle } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { ORG_ADMIN_ORG_KEY } from '@processpuzzle/org-admin';

/**
 * The landing page. Its route carries the authentication resolver, so reaching it is what sends a
 * user to Keycloak — and what makes an unauthenticated visit fail here rather than three clicks
 * later, inside a screen whose list request 401s for no visible reason.
 *
 * The cards mirror the routes: with no tenant in the URL there is no administration branch to link
 * to, so the page says which URL to use instead of offering a link that resolves to nothing.
 */
@Component({
  selector: 'app-home',
  imports: [MatCard, MatCardContent, MatCardTitle, RouterLink, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'home'">
      <h1>{{ t('title') }}</h1>
      <p class="subtitle">{{ t('subtitle') }}</p>
      @if (sections.length > 0) {
        <div class="section-cards">
          @for (section of sections; track section.link) {
            <mat-card class="section-card" [routerLink]="section.link" [attr.test-id]="'home-card-' + section.key">
              <mat-card-title>
                <span class="material-symbols-outlined">{{ section.icon }}</span>
                {{ t(section.key + '.title') }}
              </mat-card-title>
              <mat-card-content>{{ t(section.key + '.text') }}</mat-card-content>
            </mat-card>
          }
        </div>
      } @else {
        <p test-id="home-no-tenant">{{ t('noTenant') }}</p>
      }
    </ng-container>
  `,
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  private readonly orgKey = inject(ORG_ADMIN_ORG_KEY, { optional: true });

  /**
   * `organization-user` is `snakeCaseName(ORGANIZATION_USER_ENTITY_NAME)`; the segment is fixed by
   * the library, not chosen here.
   */
  readonly sections = this.orgKey ? [{ link: `/${this.orgKey}/admin/organization-user`, icon: 'group', key: 'users' }] : [];
}
