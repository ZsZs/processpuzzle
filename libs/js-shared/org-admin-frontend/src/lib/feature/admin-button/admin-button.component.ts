import { Component, computed, inject } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { ORG_ADMIN_ORG_KEY } from '../../domain/organization-user.service';

/**
 * The way into the organization's administration — its users, roles and subscription — from wherever the button is
 * placed. Draws nothing where the application has no organization to administer.
 *
 * Offered to everyone: a member who is no administrator reaches the page that says why there is nothing to administer,
 * which is what the platform's own control has always done.
 */
@Component({
  selector: 'pp-admin-button',
  template: `
    @if (link(); as target) {
      <div class="admin-button">
        <a mat-icon-button [routerLink]="target" aria-label="Administration Button">
          <mat-icon>admin_panel_settings</mat-icon>
        </a>
      </div>
    }
  `,
  styles: `
    .admin-button {
      position: relative;
      display: inline-block;
    }
  `,
  imports: [MatIcon, MatIconButton, RouterLink],
})
export class AdminButtonComponent {
  private readonly orgKey = inject(ORG_ADMIN_ORG_KEY, { optional: true });
  protected readonly link = computed(() => (this.orgKey ? ['/', this.orgKey, 'admin'] : undefined));
}
