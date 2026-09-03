import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ADMIN_URL, provideOrgAdminTesting, REALM_ROLE_DTOS, required } from '../domain/test-organization-user';
import { RoleAssignmentComponent } from './role-assignment.component';

describe('RoleAssignmentComponent', () => {
  const userRolesUrl = `${ADMIN_URL}/users/kc-1/roles`;
  let fixture: ComponentFixture<RoleAssignmentComponent>;
  let controller: HttpTestingController;
  let snackBarStub: { open: ReturnType<typeof vi.fn> };

  const render = (): HTMLElement => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  /**
   * The tab is a *sibling* of the details route under `<entity>/<id>`, so `entityId` is inherited from
   * the parent rather than declared on this route. The stub mirrors that: the own snapshot has no
   * parameters at all and only the parent carries the id.
   */
  // `null` rather than `undefined` for the no-id case: a default parameter value applies to
  // `undefined` too, so `configure(undefined)` would silently hand back the inherited `kc-1`.
  function configure(parentParams: Record<string, string> | null = { entityId: 'kc-1' }) {
    snackBarStub = { open: vi.fn() };
    TestBed.configureTestingModule({
      imports: [RoleAssignmentComponent],
      providers: [
        ...provideOrgAdminTesting(),
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: MatSnackBar, useValue: snackBarStub },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({}) },
            parent: parentParams ? { snapshot: { paramMap: convertToParamMap(parentParams) } } : null,
          },
        },
      ],
    });
    fixture = TestBed.createComponent(RoleAssignmentComponent);
    controller = TestBed.inject(HttpTestingController);
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
});
