import { Component, inject, TemplateRef, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BaseEntityContainerComponent, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { Organization } from '../domain/organization';
import { OrganizationService } from '../domain/organization.service';
import { OrganizationStore } from '../domain/organization.store';
import { createOrganizationDescriptor } from '../domain/organization.descriptors';
import { AssignAdminDialog, AssignAdminDialogData, AssignAdminDialogResult } from './assign-admin.dialog';

/**
 * The organization screens, with the three verbs that are not CRUD contributed as extra form actions.
 *
 * They are actions rather than form fields because each does something the database alone cannot:
 * suspending disables the tenant's Keycloak realm, activating re-enables it, and assigning an
 * administrator creates a user in it. The same reason the descriptor keeps `status` disabled — a form
 * that could write it would produce a tenant marked usable with no realm behind it.
 *
 * `extraFormActionsTemplate` is the seam base-rule uses for its Dry-run button; this is the same
 * mechanism with three buttons instead of one.
 *
 * Suspend and Activate are shown conditionally on the selected tenant's status, and Assign
 * administrator is hidden while it is still `PROVISIONING` — its realm does not exist yet, so the
 * backend refuses with a 409 and offering the button would only produce that error.
 */
@Component({
  selector: 'pp-organization-container',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent, MatButton],
  template: `
    <base-entity-container [entityDescriptor]="baseEntityDescriptor"></base-entity-container>
    <ng-template #lifecycleActionsTpl>
      @let organization = selectedOrganization();
      @if (organization) {
        @if (organization.isActive) {
          <button id="suspend-organization" type="button" mat-raised-button color="warn" [disabled]="busy" (click)="onSuspend(organization)">Suspend</button>
        }
        @if (organization.isSuspended) {
          <button id="activate-organization" type="button" mat-raised-button color="primary" [disabled]="busy" (click)="onActivate(organization)">Activate</button>
        }
        @if (!organization.isProvisioning) {
          <button id="assign-admin" type="button" mat-raised-button color="accent" [disabled]="busy" (click)="onAssignAdmin(organization)">Assign administrator…</button>
        }
      }
    </ng-template>
  `,
})
export class OrganizationContainerComponent {
  readonly lifecycleActionsTpl = viewChild<TemplateRef<unknown>>('lifecycleActionsTpl');
  readonly baseEntityDescriptor: BaseEntityDescriptor;

  /** Guards against a second click while a realm call is in flight; both verbs are slow by nature. */
  busy = false;

  private readonly store = inject(OrganizationStore);
  private readonly service = inject(OrganizationService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  constructor() {
    this.baseEntityDescriptor = createOrganizationDescriptor();
    this.baseEntityDescriptor.store = this.store;
    this.baseEntityDescriptor.extraFormActionsTemplate = () => this.lifecycleActionsTpl();
  }

  selectedOrganization(): Organization | undefined {
    return this.store.currentEntity() as Organization | undefined;
  }

  onSuspend(organization: Organization): void {
    this.run(this.serviceCall('suspend', organization), `Suspended ${organization.key}.`);
  }

  onActivate(organization: Organization): void {
    this.run(this.serviceCall('activate', organization), `Activated ${organization.key}.`);
  }

  onAssignAdmin(organization: Organization): void {
    const dialogRef = this.dialog.open<AssignAdminDialog, AssignAdminDialogData, AssignAdminDialogResult | undefined>(AssignAdminDialog, {
      data: { orgKey: organization.key },
    });
    dialogRef.afterClosed().subscribe((result) => {
      if (!result) return;
      this.busy = true;
      this.service.assignAdmin(organization.key, result).subscribe({
        next: (admin) => {
          this.busy = false;
          this.snackBar.open(`${admin.username} is now an administrator of ${admin.realm}. They set their own password on first sign-in.`, undefined, { duration: 6000 });
        },
        // Left to the HTTP error interceptor to report: it already opens a snackbar carrying the
        // backend's errorId, and opening a second one here would stack two messages for one failure.
        error: () => {
          this.busy = false;
        },
      });
    });
  }

  private serviceCall(verb: 'suspend' | 'activate', organization: Organization) {
    return verb === 'suspend' ? this.service.suspend(organization.key) : this.service.activate(organization.key);
  }

  private run(call: ReturnType<OrganizationService['suspend']>, message: string): void {
    this.busy = true;
    call.subscribe({
      next: () => {
        this.busy = false;
        this.snackBar.open(message, undefined, { duration: 4000 });
        // Re-read rather than patch the row in place: the status the backend settled on is the one
        // that matters, and suspend/activate can legitimately answer with a state the client did not
        // predict — a realm call that half-failed, say.
        this.store.load({});
      },
      error: () => {
        this.busy = false;
      },
    });
  }
}
