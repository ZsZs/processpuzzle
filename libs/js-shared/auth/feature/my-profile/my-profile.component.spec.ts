import { beforeEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { screen } from '@testing-library/angular';
import { ANIMATION_MODULE_TYPE, signal } from '@angular/core';
import { AUTHENTICATION_SERVICE, ROLE_DESCRIPTION_SOURCE, type RoleDescription, User } from '@processpuzzle/auth/domain';
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

  const renderComponent = async (user?: User, describeRoles?: () => Observable<ReadonlyMap<string, RoleDescription>>) => {
    return await setUpTranslocoTestBed(MyProfileComponent, testConfig, {
      providers: [
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: AUTHENTICATION_SERVICE, useValue: { currentUser: signal(user), getAccountManagementUrl } },
        { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig },
        ...(describeRoles ? [{ provide: ROLE_DESCRIPTION_SOURCE, useValue: { describeRoles } }] : []),
      ],
    });
  };

  beforeEach(() => {
    vi.clearAllMocks();
    getAccountManagementUrl.mockReturnValue('http://keycloak/realms/test/account');
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

    expect(screen.getByRole('link', { name: /Manage account/ })).toHaveAttribute('href', 'http://keycloak/realms/test/account');
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
});
