import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, FormControl, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatCheckbox } from '@angular/material/checkbox';
import { OrganizationRole, OrganizationUserInvitation } from '../domain/organization-user';

/**
 * `Validators.email` refuses a value with a leading or trailing space, and an address pasted out of a
 * mail client routinely arrives with one. The dialog trims the address before sending it, so
 * validating the untrimmed string would block an invitation it would otherwise have sent happily.
 * The error key stays `email`, which is what the template's `hasError('email')` reads.
 */
export function trimmedEmail(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' ? Validators.email(new FormControl(control.value.trim())) : Validators.email(control);
}

export interface InviteUserDialogData {
  orgKey: string;
  /** The realm's own roles, read live — see `OrganizationRoleService.findAll`. */
  roles: OrganizationRole[];
}

/**
 * What the dialog collects is exactly the contract's create payload, so it is that type rather than a
 * parallel one - the shapes cannot drift apart, and `OrganizationUserService.invite` takes it as is.
 */
export type InviteUserDialogResult = OrganizationUserInvitation;

/**
 * Collects whom to invite into the organization.
 *
 * **No password field, and there must not be one.** The backend creates the user without credentials
 * and with a required password reset, so the invitee sets their own on first login and the
 * administrator never learns it — which is also why the e-mail address is required rather than
 * optional: it is where the reset is sent.
 *
 * `org-member` is not offered as a checkbox: the backend adds it to whatever is chosen, because nav
 * visibility and workflow role assignment match on it and a user invited with only a specialised role
 * would be invisible to all of them. Showing a checkbox that cannot be cleared would be a lie.
 */
@Component({
  selector: 'pp-invite-user-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatDialogTitle, MatDialogContent, MatDialogActions, MatButton, MatFormField, MatLabel, MatError, MatInput, MatCheckbox],
  template: `
    <h2 mat-dialog-title>Invite a user into {{ data.orgKey }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="invite-user-form">
        <mat-form-field>
          <mat-label>Username</mat-label>
          <input id="invite-username" matInput formControlName="username" autocomplete="off" />
          @if (form.controls.username.hasError('required') && form.controls.username.touched) {
            <mat-error>A username is required.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>E-mail</mat-label>
          <input id="invite-email" matInput formControlName="email" type="email" autocomplete="off" />
          @if (form.controls.email.hasError('required') && form.controls.email.touched) {
            <mat-error>An e-mail address is required — the invitation is sent to it.</mat-error>
          }
          @if (form.controls.email.hasError('email')) {
            <mat-error>That is not an e-mail address.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>First name</mat-label>
          <input id="invite-first-name" matInput formControlName="firstName" autocomplete="off" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Last name</mat-label>
          <input id="invite-last-name" matInput formControlName="lastName" autocomplete="off" />
        </mat-form-field>
      </form>
      @if (assignableRoles().length > 0) {
        <fieldset class="invite-user-roles">
          <legend>Roles</legend>
          @for (role of assignableRoles(); track role.name) {
            <mat-checkbox [id]="'invite-role-' + role.name" [checked]="selected.has(role.name)" (change)="toggle(role.name)">
              {{ role.name }}
            </mat-checkbox>
          }
        </fieldset>
      }
      <p class="invite-user-note">Every member also holds <code>org-member</code>; it is granted automatically.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button id="invite-cancel" type="button" mat-button (click)="onCancel()">Cancel</button>
      <button id="invite-submit" type="button" mat-raised-button color="primary" [disabled]="form.invalid" (click)="onSubmit()">Invite</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .invite-user-form {
        display: flex;
        flex-direction: column;
        row-gap: 8px;
        min-width: 360px;
      }

      .invite-user-roles {
        display: flex;
        flex-direction: column;
        margin-top: 12px;
        border: 1px solid rgba(0, 0, 0, 0.12);
        border-radius: 4px;
        padding: 8px 12px;
      }

      .invite-user-note {
        margin: 12px 0 0;
        font-size: 0.85rem;
        opacity: 0.75;
      }
    `,
  ],
})
export class InviteUserDialog {
  readonly data = inject<InviteUserDialogData>(MAT_DIALOG_DATA);
  readonly selected = new Set<string>();

  private readonly dialogRef = inject(MatDialogRef<InviteUserDialog, InviteUserDialogResult>);
  private readonly formBuilder = inject(FormBuilder);

  readonly form = this.formBuilder.nonNullable.group({
    username: ['', Validators.required],
    email: ['', [Validators.required, trimmedEmail]],
    firstName: [''],
    lastName: [''],
  });

  /** `org-member` is filtered out because it is granted regardless; see the class comment. */
  assignableRoles(): OrganizationRole[] {
    return this.data.roles.filter((role) => role.name !== 'org-member');
  }

  toggle(roleName: string): void {
    if (this.selected.has(roleName)) this.selected.delete(roleName);
    else this.selected.add(roleName);
  }

  onCancel(): void {
    this.dialogRef.close(undefined);
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.dialogRef.close({
      username: value.username.trim(),
      email: value.email.trim(),
      // Empty strings become undefined rather than being sent: the identity provider stores what it
      // is given, and an empty first name is a value rather than the absence of one.
      firstName: value.firstName.trim() || undefined,
      lastName: value.lastName.trim() || undefined,
      roles: [...this.selected],
    });
  }
}
