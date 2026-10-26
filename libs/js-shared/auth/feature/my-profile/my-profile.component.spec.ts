import { beforeEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { fireEvent, screen, waitFor } from '@testing-library/angular';
import { ANIMATION_MODULE_TYPE, signal } from '@angular/core';
import { AUTHENTICATION_SERVICE, ROLE_DESCRIPTION_SOURCE, type RoleDescription, SelfServiceRolesService, User } from '@processpuzzle/auth/domain';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, of, throwError } from 'rxjs';
import { mockLanguageConfig, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';

import { MyProfileComponent } from './my-profile.component';
import authDe from '../assets/i18n/auth/de.json';
import authEn from '../assets/i18n/auth/en.json';

const testConfig: TranslocoTestConfig = {
  scope: 'auth',
  translations: {
    'auth/en': authEn,
    'auth/de': authDe,
  },
};

describe('MyProfileComponent', () => {
  const getAccountManagementUrl = vi.fn();
  const reloadUser = vi.fn();
  const snackBar = { open: vi.fn() };
  const selfService = { available: false, load: vi.fn(), replace: vi.fn() };

  const renderComponent = async (user?: User, describeRoles?: () => Observable<ReadonlyMap<string, RoleDescription>>) => {
    return await setUpTranslocoTestBed(MyProfileComponent, testConfig, {
      providers: [
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: AUTHENTICATION_SERVICE, useValue: { currentUser: signal(user), getAccountManagementUrl, reloadUser } },
        { provide: SelfServiceRolesService, useValue: selfService },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig },
        ...(describeRoles ? [{ provide: ROLE_DESCRIPTION_SOURCE, useValue: { describeRoles } }] : []),
      ],
    });
  };

  beforeEach(() => {
    vi.clearAllMocks();
    selfService.available = false;
    reloadUser.mockResolvedValue(undefined);
    getAccountManagementUrl.mockReturnValue('https://keycloak/realms/test/account');
  });

  it('should show the signed-in user read-only', async () => {
    await renderComponent(new User('john.doe@example.com', '123', 'John', 'Doe', null, ['org-admin', 'org-member']));

    expect(screen.getByDisplayValue('john.doe@example.com')).toHaveAttribute('readonly');
    expect(screen.getByDisplayValue('John')).toHaveAttribute('readonly');
    expect(screen.getByDisplayValue('Doe')).toHaveAttribute('readonly');
    expect(screen.getByText('org-admin')).toBeInTheDocument();
    expect(screen.getByText('org-member')).toBeInTheDocument();
  });

  it('should say so when the user holds no roles', async () => {
    await renderComponent(new User('john.doe@example.com', '123', 'John', 'Doe'));

    expect(screen.getByText(authEn.my_profile_form.no_roles)).toBeInTheDocument();
  });

  it('should leave out the built-in roles of the identity provider', async () => {
    await renderComponent(new User('john.doe@example.com', '123', 'John', 'Doe', null, ['org-member', 'offline_access', 'uma_authorization', 'default-roles-acme']));

    expect(screen.getByText('org-member')).toBeInTheDocument();
    expect(screen.queryByText('offline_access')).not.toBeInTheDocument();
    expect(screen.queryByText('uma_authorization')).not.toBeInTheDocument();
    expect(screen.queryByText('default-roles-acme')).not.toBeInTheDocument();
  });

  it('should say so when the user holds only built-in roles', async () => {
    await renderComponent(new User('john.doe@example.com', '123', 'John', 'Doe', null, ['offline_access', 'uma_authorization']));

    expect(screen.getByText(authEn.my_profile_form.no_roles)).toBeInTheDocument();
  });

  it('should describe the roles of the platform itself', async () => {
    await renderComponent(new User('john.doe@example.com', '123', 'John', 'Doe', null, ['org-admin', 'org-member']));

    expect(screen.getByText(authEn.my_profile_form.platform_roles.org_admin)).toBeInTheDocument();
    expect(screen.getByText(authEn.my_profile_form.platform_roles.org_member)).toBeInTheDocument();
  });

  it('should describe the platform administration role', async () => {
    await renderComponent(new User('john.doe@example.com', '123', 'John', 'Doe', null, ['platform-admin']));

    expect(screen.getByText(authEn.my_profile_form.platform_roles.platform_admin)).toBeInTheDocument();
  });

  it('should name and describe tenant roles from the description source', async () => {
    const describeRoles = () => of(new Map([['order-clerk', { name: 'Order Clerk', description: 'Takes and checks incoming orders.' }]]));
    await renderComponent(new User('john.doe@example.com', '123', 'John', 'Doe', null, ['order-clerk', 'reviewer']), describeRoles);

    expect(screen.getByText('Order Clerk')).toBeInTheDocument();
    expect(screen.getByText('Takes and checks incoming orders.')).toBeInTheDocument();
    expect(screen.getByText('reviewer')).toBeInTheDocument();
  });

  it('should still show the roles by id when the description source fails', async () => {
    await renderComponent(new User('john.doe@example.com', '123', 'John', 'Doe', null, ['order-clerk']), () => throwError(() => new Error('unreachable')));

    expect(screen.getByText('order-clerk')).toBeInTheDocument();
  });

  it('should link to the account page and come back to this one', async () => {
    await renderComponent(new User('john.doe@example.com', '123'));

    expect(screen.getByRole('link', { name: /Manage account/ })).toHaveAttribute('href', 'https://keycloak/realms/test/account');
    expect(getAccountManagementUrl).toHaveBeenCalledWith(document.location.href);
  });

  it('should offer no link when the provider has no account page', async () => {
    getAccountManagementUrl.mockReturnValue(undefined);
    await renderComponent(new User('john.doe@example.com', '123'));

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('should tell an anonymous visitor that nobody is signed in', async () => {
    await renderComponent(undefined);

    expect(screen.getByText(authEn.my_profile_form.not_signed_in)).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(getAccountManagementUrl).not.toHaveBeenCalled();
  });

  describe('self-service roles', () => {
    const member = () => new User('john.doe@example.com', '123', 'John', 'Doe', null, ['org-member']);
    const offered = { assigned: ['org-member'], selectable: [{ name: 'documentation-editor', description: 'Edits the handbook' }, { name: 'documentation-reviewer' }] };

    beforeEach(() => {
      selfService.available = true;
      selfService.load.mockResolvedValue(offered);
    });

    it('should offer nothing when the feature is not configured', async () => {
      selfService.available = false;
      await renderComponent(member());

      expect(selfService.load).not.toHaveBeenCalled();
      expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: authEn.my_profile_form.save_roles_button })).not.toBeInTheDocument();
    });

    it('should list the selectable roles with their descriptions, nothing checked yet', async () => {
      await renderComponent(member());

      expect(await screen.findByRole('checkbox', { name: 'documentation-editor' })).not.toBeChecked();
      expect(screen.getByRole('checkbox', { name: 'documentation-reviewer' })).not.toBeChecked();
      expect(screen.getByText('Edits the handbook')).toBeInTheDocument();
    });

    it('should leave the section out when the backend refuses', async () => {
      selfService.load.mockRejectedValue(new Error('404'));
      await renderComponent(member());

      await waitFor(() => expect(selfService.load).toHaveBeenCalled());
      expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    });

    it('should enable Save only once the selection differs', async () => {
      await renderComponent(member());
      const save = await screen.findByRole('button', { name: authEn.my_profile_form.save_roles_button });
      expect(save).toBeDisabled();

      fireEvent.click(screen.getByRole('checkbox', { name: 'documentation-editor' }));

      await waitFor(() => expect(save).toBeEnabled());
    });

    it('should save the chosen roles, reload the user and say so', async () => {
      selfService.replace.mockResolvedValue({ assigned: ['org-member', 'documentation-editor'], selectable: offered.selectable });
      await renderComponent(member());
      fireEvent.click(await screen.findByRole('checkbox', { name: 'documentation-editor' }));
      await waitFor(() => expect(screen.getByRole('button', { name: authEn.my_profile_form.save_roles_button })).toBeEnabled());

      fireEvent.click(screen.getByRole('button', { name: authEn.my_profile_form.save_roles_button }));

      await waitFor(() => expect(snackBar.open).toHaveBeenCalled());
      expect(selfService.replace).toHaveBeenCalledWith(['documentation-editor']);
      expect(reloadUser).toHaveBeenCalled();
      expect(snackBar.open).toHaveBeenCalledWith(authEn.my_profile_form.roles_saved, authEn.my_profile_form.close_button, { duration: 5000 });
      expect(screen.getByRole('checkbox', { name: 'documentation-editor' })).toBeChecked();
    });

    it('should report a failed save without reloading the user', async () => {
      selfService.replace.mockRejectedValue(new Error('boom'));
      await renderComponent(member());
      fireEvent.click(await screen.findByRole('checkbox', { name: 'documentation-editor' }));
      await waitFor(() => expect(screen.getByRole('button', { name: authEn.my_profile_form.save_roles_button })).toBeEnabled());

      fireEvent.click(screen.getByRole('button', { name: authEn.my_profile_form.save_roles_button }));

      await waitFor(() => expect(snackBar.open).toHaveBeenCalled());
      expect(reloadUser).not.toHaveBeenCalled();
      expect(snackBar.open).toHaveBeenCalledWith(authEn.my_profile_form.roles_save_failed, authEn.my_profile_form.close_button, expect.objectContaining({ panelClass: ['error-snackbar'] }));
    });
  });
});
