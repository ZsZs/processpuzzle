import { Component, inject, TemplateRef, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BaseEntityContainerComponent, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { OrganizationRole } from '../domain/organization-user';
import { ORG_ADMIN_ORG_KEY, OrganizationRoleService, OrganizationUserService } from '../domain/organization-user.service';
import { OrganizationUserStore } from '../domain/organization-user.store';
import { createOrganizationUserDescriptor } from '../domain/organization-user.descriptors';
import { ROLE_ASSIGNMENT_TAB } from './role-assignment-tab';
import { InviteUserDialog, InviteUserDialogData, InviteUserDialogResult } from './invite-user.dialog';

/**
 * The user screens, with Invite contributed as an extra form action and role assignment as an extra
 * tab.
 *
 * Invite is an action rather than the generic New button because creating a user is not a POST of the
 * form's own payload: there is no password field, the backend adds `org-member` regardless of what was
 * chosen, and the roles offered at creation come from the realm's live list. Teaching the generic New
 * all three would make it about this one entity.
 */
@Component({
  selector: 'pp-organization-user-container',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent, MatButton],
  template: `
    <base-entity-container [entityDescriptor]="baseEntityDescriptor"></base-entity-container>
    <ng-template #userActionsTpl>
      <button id="invite-user" type="button" mat-raised-button color="accent" [disabled]="busy" (click)="onInvite()">Invite user…</button>
    </ng-template>
  `,
})
export class OrganizationUserContainerComponent {
  readonly userActionsTpl = viewChild<TemplateRef<unknown>>('userActionsTpl');
  readonly baseEntityDescriptor: BaseEntityDescriptor;

  /** Guards against a second click while the realm read or the invitation is in flight. */
  busy = false;

  private readonly store = inject(OrganizationUserStore);
  private readonly userService = inject(OrganizationUserService);
  private readonly roleService = inject(OrganizationRoleService);
  private readonly orgKey = inject(ORG_ADMIN_ORG_KEY);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  constructor() {
    this.baseEntityDescriptor = createOrganizationUserDescriptor();
    this.baseEntityDescriptor.store = this.store;
    this.baseEntityDescriptor.extraTabs = [ROLE_ASSIGNMENT_TAB];
    this.baseEntityDescriptor.extraFormActionsTemplate = () => this.userActionsTpl();
  }

  onInvite(): void {
    // The realm's roles are read now rather than cached: they are the tenant's own, and one may have
    // been added since this screen was loaded.
    this.busy = true;
    this.roleService.findAll().subscribe({
      next: (roles) => {
        this.busy = false;
        this.openInviteDialog(roles);
      },
      // Left to the HTTP error interceptor, which already opens a snackbar carrying the backend's
      // errorId; a second one here would stack two messages for one failure.
      error: () => {
        this.busy = false;
      },
    });
  }

  private openInviteDialog(roles: OrganizationRole[]): void {
    const dialogRef = this.dialog.open<InviteUserDialog, InviteUserDialogData, InviteUserDialogResult | undefined>(InviteUserDialog, {
      data: { orgKey: this.orgKey, roles },
    });
    dialogRef.afterClosed().subscribe((result) => {
      if (result) this.invite(result);
    });
  }

  private invite(result: InviteUserDialogResult): void {
    this.busy = true;
    // `invite` rather than `add`, and not through the store at all: the invitation payload is neither
    // the form's nor the one `toDto` produces - that one drops `username`, which the contract requires -
    // and routing it through the store would leave `currentEntity` as the half-built user.
    this.userService.invite(result).subscribe({
      next: (user) => {
        this.busy = false;
        this.snackBar.open(`Invited ${user.username}. They set their own password on first sign-in.`, undefined, { duration: 6000 });
        // Re-read rather than push the returned row in: the identity provider decides what it stored,
        // and its answer is what the list should show.
        this.store.load({});
      },
      error: () => {
        this.busy = false;
      },
    });
  }
}
