import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';

export interface AssignAdminDialogData {
  orgKey: string;
}

export interface AssignAdminDialogResult {
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
}

/**
 * Collects who to make an administrator of a tenant.
 *
 * **There is no password field, and there must not be.** The backend creates the user without
 * credentials and with a required password reset, so the invitee sets their own on first login and
 * whoever fills this form never learns it. Adding a field here would mean staff choosing a password
 * for a customer's administrator and having to transmit it somehow.
 *
 * Username and email are both required because the identity provider needs the first to sign in with
 * and the second to send the reset to. First and last name are not: a tenant may not know them yet,
 * and blocking the invitation over a display name would be absurd.
 */
@Component({
  selector: 'pp-assign-admin-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatDialogTitle, MatDialogContent, MatDialogActions, MatButton, MatFormField, MatLabel, MatError, MatInput],
  template: `
    <h2 mat-dialog-title>Assign an administrator of {{ data.orgKey }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="assign-admin-form">
        <mat-form-field>
          <mat-label>Username</mat-label>
          <input id="assign-admin-username" matInput formControlName="username" autocomplete="off" />
          @if (form.controls.username.hasError('required') && form.controls.username.touched) {
            <mat-error>A username is required.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>E-mail</mat-label>
          <input id="assign-admin-email" matInput formControlName="email" type="email" autocomplete="off" />
          @if (form.controls.email.hasError('required') && form.controls.email.touched) {
            <mat-error>An e-mail address is required — the password reset is sent to it.</mat-error>
          }
          @if (form.controls.email.hasError('email')) {
            <mat-error>That is not an e-mail address.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>First name</mat-label>
          <input id="assign-admin-first-name" matInput formControlName="firstName" autocomplete="off" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Last name</mat-label>
          <input id="assign-admin-last-name" matInput formControlName="lastName" autocomplete="off" />
        </mat-form-field>
      </form>
      <p class="assign-admin-note">
        The invitee sets their own password on first sign-in. No password is set here, and none is sent.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button id="assign-admin-cancel" type="button" mat-button (click)="onCancel()">Cancel</button>
      <button id="assign-admin-submit" type="button" mat-raised-button color="primary" [disabled]="form.invalid" (click)="onSubmit()">Assign</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .assign-admin-form {
        display: flex;
        flex-direction: column;
        row-gap: 8px;
        min-width: 360px;
      }

      .assign-admin-note {
        margin: 12px 0 0;
        font-size: 0.85rem;
        opacity: 0.75;
      }
    `,
  ],
})
export class AssignAdminDialog {
  readonly data = inject<AssignAdminDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<AssignAdminDialog, AssignAdminDialogResult>);
  private readonly formBuilder = inject(FormBuilder);

  readonly form = this.formBuilder.nonNullable.group({
    username: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    firstName: [''],
    lastName: [''],
  });

  onCancel(): void {
    this.dialogRef.close();
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.dialogRef.close({
      username: value.username.trim(),
      email: value.email.trim(),
      // Empty strings become undefined rather than being sent: the identity provider stores what it
      // is given, and an empty first name is a value, not the absence of one.
      firstName: value.firstName.trim() || undefined,
      lastName: value.lastName.trim() || undefined,
    });
  }
}
