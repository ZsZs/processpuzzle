import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NavigateBackService } from '@processpuzzle/util';
import { BehaviorSubject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ADA_DTO, ADMIN_URL, GRACE_DTO, provideOrgAdminTesting, REALM_ROLE_DTOS, required, USERS_URL } from '../domain/test-organization-user';
import { RoleAssignmentComponent } from './role-assignment.component';

describe('RoleAssignmentComponent', () => {
  const userRolesUrl = `${ADMIN_URL}/users/kc-1/roles`;
  let fixture: ComponentFixture<RoleAssignmentComponent>;
  let controller: HttpTestingController;
  let snackBarStub: { open: ReturnType<typeof vi.fn> };
  let paramsOfRoute: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let routerStub: { navigate: ReturnType<typeof vi.fn> };
  let navigateBackStub: { goBack: ReturnType<typeof vi.fn>; getRouteStack: ReturnType<typeof vi.fn> };
  /** Entries in the navigation history: a page opened directly has none to go back to. */
  let historySize: number;

  const render = (): HTMLElement => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  /**
   * The tab is declared as `:entityId/roles`, so `entityId` is a parameter of this route itself. The stub
   * mirrors the router: the id arrives on the route's own paramMap, and the parent has none.
   */
  // `null` rather than `undefined` for the no-id case: a default parameter value applies to
  // `undefined` too, so `configure(undefined)` would silently hand back the default `kc-1`.
  function configure(params: Record<string, string> | null = { entityId: 'kc-1' }) {
    snackBarStub = { open: vi.fn() };
    paramsOfRoute = new BehaviorSubject(convertToParamMap(params ?? {}));
    historySize = 0;
    routerStub = { navigate: vi.fn().mockResolvedValue(true) };
    navigateBackStub = { goBack: vi.fn(), getRouteStack: vi.fn(() => ({ size: () => historySize })) };
    TestBed.configureTestingModule({
      imports: [RoleAssignmentComponent],
      providers: [
        ...provideOrgAdminTesting(),
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: MatSnackBar, useValue: snackBarStub },
        { provide: Router, useValue: routerStub },
        { provide: NavigateBackService, useValue: navigateBackStub },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(params ?? {}) },
            paramMap: paramsOfRoute,
            parent: { snapshot: { paramMap: convertToParamMap({}) }, paramMap: of(convertToParamMap({})) },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(RoleAssignmentComponent);
    controller = TestBed.inject(HttpTestingController);
    // The user store lists the users when it is created, which is not what these tests are about.
    controller.match(USERS_URL).forEach((request) => request.flush([ADA_DTO, GRACE_DTO]));
  }

  /** The re-read of the user after a save: one user by id, carrying the roles the save just made. */
  function refreshUser(roles: { name: string }[]) {
    controller.expectOne(`${USERS_URL}/kc-1`).flush({ ...ADA_DTO, roles: roles.map((role) => role.name) });
  }

  function load(held: string[] = ['org-member', 'accountant']) {
    render();
    controller.expectOne(`${ADMIN_URL}/roles`).flush(REALM_ROLE_DTOS);
    controller.expectOne(userRolesUrl).flush(held.map((name) => ({ name })));
    return render();
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    configure();
  });

  // Read from the realm rather than from a fixed list: beyond org-admin and org-member the roles are
  // the tenant's own, and they are what NavNode.roles and workflow role definitions match against.
  it('offers every realm role, with the two ProcessPuzzle manages marked', () => {
    const host = load();

    expect(host.querySelectorAll('mat-checkbox')).toHaveLength(3);
    expect(required(host, '#role-org-admin').textContent).toContain('(ProcessPuzzle)');
    expect(required(host, '#role-accountant').textContent).not.toContain('(ProcessPuzzle)');
  });

  it('checks the roles the user already holds', () => {
    load(['accountant']);

    expect([...fixture.componentInstance.selected()]).toEqual(['accountant']);
  });

  it('shows a progress bar until both reads have answered', () => {
    const host = render();
    expect(host.querySelector('mat-progress-bar')).not.toBeNull();

    controller.expectOne(`${ADMIN_URL}/roles`).flush(REALM_ROLE_DTOS);
    controller.expectOne(userRolesUrl).flush([]);

    expect(render().querySelector('mat-progress-bar')).toBeNull();
  });

  /**
   * A full replacement, not add and remove: two people editing this screen at once must not silently
   * merge into a union neither of them chose.
   */
  it('saves the whole set in one PUT', () => {
    const host = load(['org-member']);

    fixture.componentInstance.toggle('accountant');
    fixture.detectChanges();
    required<HTMLButtonElement>(host, '#save-roles').click();

    const request = controller.expectOne(userRolesUrl);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ roles: ['org-member', 'accountant'] });
    request.flush([{ name: 'org-member' }, { name: 'accountant' }]);
  });

  // Adopt what the server reports, not what was sent: a role revoked concurrently by somebody else is
  // absent from the response, and keeping the local set would show a grant that no longer exists.
  it('adopts the answer rather than the payload it sent', () => {
    load(['org-member', 'accountant']);

    fixture.componentInstance.onSave();
    controller.expectOne(userRolesUrl).flush([{ name: 'org-member' }]);

    expect([...fixture.componentInstance.selected()]).toEqual(['org-member']);
    // Revert now returns to what the server holds, so the concurrent revocation cannot be undone by
    // a stale local snapshot.
    fixture.componentInstance.toggle('accountant');
    fixture.componentInstance.onRevert();
    expect([...fixture.componentInstance.selected()]).toEqual(['org-member']);
  });

  // The API answers at once but an already-issued token keeps the old roles, so the delay is stated
  // rather than left for the user to discover.
  it('says a change reaches the token only at the next sign-in', () => {
    const host = load();

    expect(host.textContent).toContain('next sign-in');

    fixture.componentInstance.onSave();
    controller.expectOne(userRolesUrl).flush([]);

    expect(snackBarStub.open.mock.calls[0][0]).toContain('next sign-in');
  });

  /**
   * Save and Revert return the user to where they came from, which is the details after the tab. A page
   * opened directly has no such history, so the details are the destination.
   */
  it('goes back to where the user came from after a save', async () => {
    historySize = 2;
    load(['org-member']);

    fixture.componentInstance.toggle('accountant');
    fixture.componentInstance.onSave();
    controller.expectOne(userRolesUrl).flush([{ name: 'org-member' }, { name: 'accountant' }]);
    await fixture.whenStable();

    // Not before the user has been re-read: the details screen would otherwise show the roles from before the save.
    expect(navigateBackStub.goBack).not.toHaveBeenCalled();
    refreshUser([{ name: 'org-member' }, { name: 'accountant' }]);

    await vi.waitFor(() => expect(navigateBackStub.goBack).toHaveBeenCalledOnce());
    expect(routerStub.navigate).not.toHaveBeenCalled();
  });

  it('stays on the page when the save is refused, so the edit can be retried', () => {
    historySize = 2;
    load(['org-member']);

    fixture.componentInstance.onSave();
    controller.expectOne(userRolesUrl).flush({ errorId: 'roles.refused' }, { status: 500, statusText: 'Server Error' });

    expect(navigateBackStub.goBack).not.toHaveBeenCalled();
    expect(routerStub.navigate).not.toHaveBeenCalled();
  });

  it('returns to the details when Revert is pressed on a page opened directly', () => {
    historySize = 1;
    load(['accountant']);

    fixture.componentInstance.onRevert();

    expect(navigateBackStub.goBack).not.toHaveBeenCalled();
    expect(routerStub.navigate).toHaveBeenCalledWith(['../details'], expect.objectContaining({ relativeTo: expect.anything() }));
  });

  it('goes back when Revert is pressed after arriving from the details', () => {
    historySize = 2;
    load(['accountant']);

    fixture.componentInstance.onRevert();

    expect(navigateBackStub.goBack).toHaveBeenCalledOnce();
  });

  /**
   * Save and Revert only act on a difference. With nothing changed there is nothing to save or to discard,
   * so both stay disabled until a checkbox differs from the roles the user holds, and again once it is back.
   */
  it('enables Save and Revert only while the checkboxes differ from the roles held', () => {
    const host = load(['org-member']);
    const saveButton = required<HTMLButtonElement>(host, '#save-roles');
    const revertButton = required<HTMLButtonElement>(host, '#revert-roles');

    expect(saveButton.disabled).toBe(true);
    expect(revertButton.disabled).toBe(true);

    fixture.componentInstance.toggle('accountant');
    fixture.detectChanges();
    expect(saveButton.disabled).toBe(false);
    expect(revertButton.disabled).toBe(false);

    fixture.componentInstance.toggle('accountant');
    fixture.detectChanges();
    expect(saveButton.disabled).toBe(true);
    expect(revertButton.disabled).toBe(true);
  });

  it('reverts to the roles the user held', () => {
    load(['accountant']);

    fixture.componentInstance.toggle('org-admin');
    fixture.componentInstance.onRevert();

    expect([...fixture.componentInstance.selected()]).toEqual(['accountant']);
  });

  it('leaves the save button alone when a failure comes back, so the edit can be retried', () => {
    load();

    fixture.componentInstance.onSave();
    controller.expectOne(userRolesUrl).flush({ errorId: 'role.unknown' }, { status: 400, statusText: 'Bad Request' });

    expect(fixture.componentInstance.saving()).toBe(false);
    // No snackbar here: the HTTP error interceptor already opens one carrying the backend's errorId,
    // and a second would stack two messages for one failure.
    expect(snackBarStub.open).not.toHaveBeenCalled();
  });

  /**
   * Without an id there is nobody to assign to. The screen still lists the realm's roles - it is
   * reachable only under `<entity>/<id>`, so this is a defensive path rather than an expected one -
   * but it must not issue a PUT to a URL with `undefined` in it.
   */
  it('never saves when no user id was inherited', () => {
    TestBed.resetTestingModule();
    configure(null);
    render();
    controller.expectOne(`${ADMIN_URL}/roles`).flush(REALM_ROLE_DTOS);

    required<HTMLButtonElement>(render(), '#save-roles').click();
    fixture.componentInstance.onSave();

    controller.verify();
  });

  /**
   * A failed read leaves no baseline. Shown as an empty set, the administrator could save it and revoke
   * roles the user was never shown; so it is shown as a failure, with nothing to save, until a retry
   * reads both again.
   */
  it('refuses to save a set it never read, and reads again on retry', () => {
    render();
    controller.expectOne(`${ADMIN_URL}/roles`).flush(REALM_ROLE_DTOS);
    controller.expectOne(userRolesUrl).flush({ errorId: 'user-directory.unavailable' }, { status: 503, statusText: 'Unavailable' });

    const host = render();
    expect(host.querySelector('mat-checkbox')).toBeNull();
    expect(host.textContent).toContain('could not be read');
    expect(host.querySelector('#save-roles')).toBeNull();

    fixture.componentInstance.onSave();
    controller.verify();

    required<HTMLButtonElement>(host, '#retry-roles').click();
    controller.expectOne(`${ADMIN_URL}/roles`).flush(REALM_ROLE_DTOS);
    controller.expectOne(userRolesUrl).flush([{ name: 'accountant' }]);
    render();

    expect(fixture.componentInstance.failed()).toBe(false);
    expect([...fixture.componentInstance.selected()]).toEqual(['accountant']);
  });

  /**
   * The route is reused for another user, so an answer still in flight for the previous one must not
   * land in this editor: it would show, and save, the wrong person's roles.
   */
  it('starts over for another user and drops the answers still in flight for the last one', () => {
    render();
    controller.expectOne(`${ADMIN_URL}/roles`).flush(REALM_ROLE_DTOS);
    const previous = controller.expectOne(userRolesUrl);

    paramsOfRoute.next(convertToParamMap({ entityId: 'kc-2' }));

    expect(previous.cancelled).toBe(true);
    controller.expectOne(`${ADMIN_URL}/roles`).flush(REALM_ROLE_DTOS);
    controller.expectOne(`${ADMIN_URL}/users/kc-2/roles`).flush([{ name: 'org-member' }]);

    expect(fixture.componentInstance.userId).toBe('kc-2');
    expect([...fixture.componentInstance.selected()]).toEqual(['org-member']);
  });
});
