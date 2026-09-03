import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Organization, OrganizationStatus } from '../domain/organization';
import { OrganizationService } from '../domain/organization.service';
import { OrganizationStore } from '../domain/organization.store';
import { OrganizationContainerComponent } from './organization-container.component';

describe('OrganizationContainerComponent', () => {
  let component: OrganizationContainerComponent;
  let store: { currentEntity: ReturnType<typeof vi.fn>; load: ReturnType<typeof vi.fn> };
  let service: { suspend: ReturnType<typeof vi.fn>; activate: ReturnType<typeof vi.fn>; assignAdmin: ReturnType<typeof vi.fn> };
  let dialog: { open: ReturnType<typeof vi.fn> };
  let snackBar: { open: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    store = { currentEntity: vi.fn(), load: vi.fn() };
    service = { suspend: vi.fn(), activate: vi.fn(), assignAdmin: vi.fn() };
    dialog = { open: vi.fn() };
    snackBar = { open: vi.fn() };
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: OrganizationStore, useValue: store },
        { provide: OrganizationService, useValue: service },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    component = TestBed.runInInjectionContext(() => new OrganizationContainerComponent());
  });

  it('exposes the selected organization and refreshes after lifecycle changes', () => {
    const active = new Organization({ key: 'acme', status: OrganizationStatus.ACTIVE });
    store.currentEntity.mockReturnValue(active);
    service.suspend.mockReturnValue(of(new Organization({ key: 'acme', status: OrganizationStatus.SUSPENDED })));
    service.activate.mockReturnValue(of(active));

    expect(component.selectedOrganization()).toBe(active);
    component.onSuspend(active);
    component.onActivate(new Organization({ key: 'acme', status: OrganizationStatus.SUSPENDED }));

    expect(service.suspend).toHaveBeenCalledWith('acme');
    expect(service.activate).toHaveBeenCalledWith('acme');
    expect(store.load).toHaveBeenCalledTimes(2);
    expect(snackBar.open).toHaveBeenCalledWith('Suspended acme.', undefined, { duration: 4000 });
    expect(snackBar.open).toHaveBeenCalledWith('Activated acme.', undefined, { duration: 4000 });
    expect(component.busy).toBe(false);
  });

  it('releases the busy guard when a lifecycle action fails', () => {
    service.suspend.mockReturnValue(throwError(() => new Error('failed')));

    component.onSuspend(new Organization({ key: 'acme' }));

    expect(component.busy).toBe(false);
    expect(store.load).not.toHaveBeenCalled();
  });

  it('assigns an administrator only when the dialog returns one', () => {
    const organization = new Organization({ key: 'acme' });
    dialog.open.mockReturnValue({ afterClosed: () => of({ username: 'ada', email: 'ada@example.test' }) });
    service.assignAdmin.mockReturnValue(of({ id: '1', username: 'ada', realm: 'acme' }));

    component.onAssignAdmin(organization);

    expect(dialog.open).toHaveBeenCalled();
    expect(service.assignAdmin).toHaveBeenCalledWith('acme', { username: 'ada', email: 'ada@example.test' });
    expect(snackBar.open).toHaveBeenCalledWith('ada is now an administrator of acme. They set their own password on first sign-in.', undefined, { duration: 6000 });
    expect(component.busy).toBe(false);
  });

  it('does nothing when the administrator dialog is cancelled and releases busy after an error', () => {
    const organization = new Organization({ key: 'acme' });
    dialog.open.mockReturnValueOnce({ afterClosed: () => of(undefined) }).mockReturnValueOnce({ afterClosed: () => of({ username: 'ada', email: 'ada@example.test' }) });
    service.assignAdmin.mockReturnValue(throwError(() => new Error('failed')));

    component.onAssignAdmin(organization);
    expect(service.assignAdmin).not.toHaveBeenCalled();

    component.onAssignAdmin(organization);
    expect(component.busy).toBe(false);
  });
});
