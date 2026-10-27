import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCard, MatCardActions, MatCardContent, MatCardTitle } from '@angular/material/card';
import { MatButton } from '@angular/material/button';
import { ORG_ADMIN_ORG_KEY } from '../domain/organization-user.service';

/**
 * The administration overview: one card per thing an administrator manages, and nothing else.
 *
 * It is the landing page of the administration branch, so the sidenav's Administration entry opens here
 * rather than straight into the users. The cards link to the two screens; the links are absolute, built from
 * the organization key, because this page is reached at the branch root and its own route has no path to
 * resolve a relative link against.
 */
@Component({
  selector: 'pp-organization-administration',
  standalone: true,
  imports: [RouterLink, MatCard, MatCardTitle, MatCardContent, MatCardActions, MatButton],
  template: `
    <div class="administration-grid">
      <mat-card id="administration-users" appearance="outlined">
        <mat-card-title>Users</mat-card-title>
        <mat-card-content>
          List and edit the users of the organization, and assign their roles.
        </mat-card-content>
        <mat-card-actions>
          <a mat-button [routerLink]="['/', orgKey, 'admin', 'organization-user']">Open users</a>
        </mat-card-actions>
      </mat-card>
      <mat-card id="administration-roles" appearance="outlined">
        <mat-card-title>Roles</mat-card-title>
        <mat-card-content>
          List and edit the roles of the organization. Roles of ProcessPuzzle and of workflow definitions are read-only.
        </mat-card-content>
        <mat-card-actions>
          <a mat-button [routerLink]="['/', orgKey, 'admin', 'organization-role']">Open roles</a>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [
    `
      .administration-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
        gap: 16px;
        padding: 16px;
      }
    `,
  ],
})
export class OrganizationAdministrationComponent {
  readonly orgKey = inject(ORG_ADMIN_ORG_KEY);
}
