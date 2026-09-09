import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OrganizationRole } from '../domain/organization-user';
import { required } from '../domain/test-organization-user';
import { InviteUserDialog, InviteUserDialogData } from './invite-user.dialog';

describe('InviteUserDialog', () => {
  const dialogData: InviteUserDialogData = {
    orgKey: 'my-org',
    roles: [new OrganizationRole('org-admin', 'Administers the organization.', true), new OrganizationRole('org-member', 'Every member holds it.', true), new OrganizationRole('accountant', undefined, false)],
  };
  let dialogRefStub: { close: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<InviteUserDialog>;
  let host: HTMLElement;

  const render = (): HTMLElement => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  const fill = (values: { username?: string; email?: string; firstName?: string; lastName?: string }) => fixture.componentInstance.form.patchValue(values);

  beforeEach(async () => {
    dialogRefStub = { close: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [InviteUserDialog],
      providers: [
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: MAT_DIALOG_DATA, useValue: dialogData },
        { provide: MatDialogRef, useValue: dialogRefStub },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(InviteUserDialog);
    host = render();
  });

  /**
   * The single most important assertion in this file. The backend creates the account with no
   * credentials and a required reset, so the invitee sets their own password on first sign-in and the
   * administrator never learns it. A password field here would quietly undo that.
   */
  it('offers no password field', () => {
    expect(host.querySelector('input[type="password"]')).toBeNull();
    expect(Object.keys(fixture.componentInstance.form.controls)).toEqual(['username', 'email', 'firstName', 'lastName']);
  });

  it('cannot be submitted without a username and an e-mail address', () => {
    expect(required<HTMLButtonElement>(host, '#invite-submit').disabled).toBe(true);

    fill({ username: 'ada', email: 'ada@my-org.example' });

    expect(render().querySelector<HTMLButtonElement>('#invite-submit')?.disabled).toBe(false);
  });

  // The address is where the password-reset invitation is sent, so a typo is not a cosmetic problem.
  it('rejects a malformed e-mail address', () => {
    fill({ username: 'ada', email: 'not-an-address' });

    expect(fixture.componentInstance.form.controls.email.hasError('email')).toBe(true);
    expect(render().querySelector<HTMLButtonElement>('#invite-submit')?.disabled).toBe(true);
  });

  // Pasting an address out of a mail client routinely brings a stray space along. The dialog trims
  // before sending, so refusing the untrimmed string would block an invitation it would then send.
  it('accepts an address pasted with surrounding whitespace', () => {
    fill({ username: 'ada', email: ' ada@my-org.example ' });

    expect(fixture.componentInstance.form.controls.email.hasError('email')).toBe(false);
    expect(render().querySelector<HTMLButtonElement>('#invite-submit')?.disabled).toBe(false);
  });

  /**
   * `org-member` is granted by the backend regardless of what was chosen, because nav visibility and
   * workflow role assignment both match on it. A checkbox that cannot be cleared would be a lie, so
   * the role is stated in prose instead.
   */
  it('leaves org-member out of the checkboxes and says so in prose', () => {
    expect(fixture.componentInstance.assignableRoles().map((role) => role.name)).toEqual(['org-admin', 'accountant']);
    expect(host.querySelector('#invite-role-org-member')).toBeNull();
    expect(host.querySelector('#invite-role-accountant')).not.toBeNull();
    expect(host.textContent).toContain('org-member');
  });

  it('closes with the contract’s create payload', () => {
    fill({ username: '  ada  ', email: ' ada@my-org.example ', firstName: 'Ada', lastName: 'Lovelace' });
    fixture.componentInstance.toggle('accountant');

    fixture.componentInstance.onSubmit();

    expect(dialogRefStub.close).toHaveBeenCalledWith({
      username: 'ada',
      email: 'ada@my-org.example',
      firstName: 'Ada',
      lastName: 'Lovelace',
      roles: ['accountant'],
    });
  });

  // An empty first name is a value, not the absence of one - the identity provider stores what it is
  // given, and a blank string would overwrite nothing with something.
  it('sends an omitted name as undefined rather than as an empty string', () => {
    fill({ username: 'ada', email: 'ada@my-org.example' });

    fixture.componentInstance.onSubmit();

    expect(dialogRefStub.close).toHaveBeenCalledWith({ username: 'ada', email: 'ada@my-org.example', firstName: undefined, lastName: undefined, roles: [] });
  });

  it('toggles a role off again', () => {
    fixture.componentInstance.toggle('accountant');
    fixture.componentInstance.toggle('accountant');

    expect([...fixture.componentInstance.selected]).toEqual([]);
  });

  it('does not close with a half-filled form', () => {
    fill({ username: 'ada' });

    fixture.componentInstance.onSubmit();

    expect(dialogRefStub.close).not.toHaveBeenCalled();
  });

  it('closes with nothing on cancel, so the caller can tell it apart from an empty invitation', () => {
    required<HTMLButtonElement>(host, '#invite-cancel').click();

    expect(dialogRefStub.close).toHaveBeenCalledWith(undefined);
  });
});
