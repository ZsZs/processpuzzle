import { beforeEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { screen } from '@testing-library/angular';
import { ANIMATION_MODULE_TYPE, signal } from '@angular/core';
import { AUTHENTICATION_SERVICE, User } from '@processpuzzle/auth/domain';
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

  const renderComponent = async (user?: User) => {
    return await setUpTranslocoTestBed(MyProfileComponent, testConfig, {
      providers: [
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: AUTHENTICATION_SERVICE, useValue: { currentUser: signal(user), getAccountManagementUrl } },
        { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig },
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
