import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ADA_DTO, ADMIN_URL, GRACE_DTO, provideOrgAdminTesting, REALM_ROLE_DTOS, USERS_URL } from '../domain/test-organization-user';
import { OrganizationUserStore } from '../domain/organization-user.store';
import { ROLE_ASSIGNMENT_TAB } from './role-assignment-tab';
import { InviteUserDialogResult } from './invite-user.dialog';
import { OrganizationUserContainerComponent } from './organization-user-container.component';

describe('OrganizationUserContainerComponent', () => {
  const invitation: InviteUserDialogResult = { username: 'ada', email: 'ada@my-org.example', firstName: 'Ada', lastName: undefined, roles: ['accountant'] };
  let fixture: ComponentFixture<OrganizationUserContainerComponent>;
  let controller: HttpTestingController;
  let dialogStub: { open: ReturnType<typeof vi.fn> };
  let snackBarStub: { open: ReturnType<typeof vi.fn> };
  let closedWith: InviteUserDialogResult | undefined;

  beforeEach(() => {
    TestBed.resetTestingModule();
    closedWith = invitation;
    snackBarStub = { open: vi.fn() };
    dialogStub = { open: vi.fn(() => ({ afterClosed: () => of(closedWith) }) as unknown as MatDialogRef<unknown>) };
    TestBed.configureTestingModule({
      imports: [OrganizationUserContainerComponent],
      providers: [
        ...provideOrgAdminTesting(),
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: MatDialog, useValue: dialogStub },
        { provide: MatSnackBar, useValue: snackBarStub },
      ],
      // The template is blanked, the way base-app's container specs do it. What is under test is the
      // descriptor this component builds and the two calls it makes, not the generic container it
      // wraps - and rendering that one would pull in the router, Transloco and a tab bar whose
      // `ngOnInit` is what binds the store it reads on destroy.
    })
      .overrideComponent(OrganizationUserContainerComponent, { set: { template: '', imports: [] } });
    fixture = TestBed.createComponent(OrganizationUserContainerComponent);
    controller = TestBed.inject(HttpTestingController);
    // BaseEntityStore lists on init, so the collection GET is already in flight before the first
    // assertion - see [[base-entity-store-lists-on-init]].
    controller.expectOne(USERS_URL).flush([ADA_DTO, GRACE_DTO]);
  });

  /**
   * The tab is contributed here rather than by the facade, the way base-app and base-document do it:
   * the route it links to exists only under this library's own routes, so a descriptor resolved
   * anywhere else would render a dead link.
   */
  it('contributes the role-assignment tab and binds the shared store', () => {
    const descriptor = fixture.componentInstance.baseEntityDescriptor;

    expect(descriptor.extraTabs).toEqual([ROLE_ASSIGNMENT_TAB]);
    expect(descriptor.store).toBe(TestBed.inject(OrganizationUserStore));
    // The template itself is blanked here; that the contribution is wired is what this pins.
    expect(descriptor.extraFormActionsTemplate).toBeDefined();
  });

  /**
   * The realm's roles are read when the button is pressed rather than cached with the screen: they are
   * the tenant's own, and one may have been added since the list was loaded.
   */
  it('reads the realm roles before opening the dialog', () => {
    fixture.componentInstance.onInvite();

    expect(fixture.componentInstance.busy).toBe(true);
    controller.expectOne(`${ADMIN_URL}/roles`).flush(REALM_ROLE_DTOS);

    expect(dialogStub.open).toHaveBeenCalledOnce();
    expect(dialogStub.open.mock.calls[0][1].data.orgKey).toBe('my-org');
    expect(dialogStub.open.mock.calls[0][1].data.roles.map((role: { name: string }) => role.name)).toEqual(['org-admin', 'org-member', 'accountant']);
  });

  /**
   * The regression this method exists for. `add` would serialise through `toDto`, which models the
   * *update* payload and drops `username` - and the contract requires it, so every invitation would
   * come back 400 from a screen that looked like it had worked.
   */
  it('posts the invitation itself, username and roles included', () => {
    fixture.componentInstance.onInvite();
    controller.expectOne(`${ADMIN_URL}/roles`).flush(REALM_ROLE_DTOS);

    const request = controller.expectOne(USERS_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(invitation);
    request.flush(ADA_DTO);

    // Re-read rather than push the returned row in: the identity provider decides what it stored.
    controller.expectOne(USERS_URL).flush([ADA_DTO, GRACE_DTO]);
    expect(fixture.componentInstance.busy).toBe(false);
    expect(snackBarStub.open.mock.calls[0][0]).toContain('ada');
  });

  it('does nothing when the dialog is dismissed', () => {
    closedWith = undefined;

    fixture.componentInstance.onInvite();
    controller.expectOne(`${ADMIN_URL}/roles`).flush(REALM_ROLE_DTOS);

    controller.verify();
    expect(fixture.componentInstance.busy).toBe(false);
  });

  it('releases the button when the realm read fails, so Invite can be pressed again', () => {
    fixture.componentInstance.onInvite();

    controller.expectOne(`${ADMIN_URL}/roles`).flush({ errorId: 'realm.unreachable' }, { status: 503, statusText: 'Service Unavailable' });

    expect(fixture.componentInstance.busy).toBe(false);
    expect(dialogStub.open).not.toHaveBeenCalled();
    // No snackbar here: the HTTP error interceptor already opens one for the same failure.
    expect(snackBarStub.open).not.toHaveBeenCalled();
  });

  it('releases the button when the invitation is refused', () => {
    fixture.componentInstance.onInvite();
    controller.expectOne(`${ADMIN_URL}/roles`).flush(REALM_ROLE_DTOS);
    controller.expectOne(USERS_URL).flush({ errorId: 'user.exists' }, { status: 409, statusText: 'Conflict' });

    expect(fixture.componentInstance.busy).toBe(false);
    expect(snackBarStub.open).not.toHaveBeenCalled();
  });
});
