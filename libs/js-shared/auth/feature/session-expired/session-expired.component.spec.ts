import { beforeEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { fireEvent, screen } from '@testing-library/angular';
import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';
import { mockLanguageConfig, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';

import { SessionExpiredComponent } from './session-expired.component';
import authDe from '../assets/i18n/auth/de.json';
import authEn from '../assets/i18n/auth/en.json';

const testConfig: TranslocoTestConfig = {
  scope: 'auth',
  translations: { 'auth/en': authEn, 'auth/de': authDe },
};

describe('SessionExpiredComponent', () => {
  const authService = {
    clearSession: vi.fn(),
    login: vi.fn(),
    isAuthenticated: vi.fn(),
  };
  const router = { navigateByUrl: vi.fn().mockResolvedValue(true) };

  const render = (returnUrl: string | null) =>
    setUpTranslocoTestBed(SessionExpiredComponent, testConfig, {
      providers: [
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: AUTHENTICATION_SERVICE, useValue: authService },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => returnUrl } } } },
        { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig },
      ],
    });

  beforeEach(() => {
    vi.clearAllMocks();
    authService.login.mockResolvedValue(undefined);
    authService.isAuthenticated.mockReturnValue(false);
  });

  it('explains that the session has ended and offers to sign in again and to go back', async () => {
    await render(null);

    expect(screen.getByRole('heading', { name: /your session has ended/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in again/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /start page/i })).toBeInTheDocument();
  });

  it('clears the session and logs in, returning to the page the user was on', async () => {
    await render('acme/orders?page=2');

    fireEvent.click(screen.getByRole('button', { name: /sign in again/i }));

    expect(authService.clearSession).toHaveBeenCalledTimes(1);
    expect(authService.login).toHaveBeenCalledWith('acme/orders?page=2');
    expect(authService.clearSession.mock.invocationCallOrder[0]).toBeLessThan(authService.login.mock.invocationCallOrder[0]);
  });

  it.each(['https://evil.example/phish', '//evil.example', 'javascript:alert(1)', 'a\\b'])('does not return to a foreign address (%s)', async (returnUrl) => {
    await render(returnUrl);

    fireEvent.click(screen.getByRole('button', { name: /sign in again/i }));

    expect(authService.login).toHaveBeenCalledWith('');
  });

  it('goes on to the page when the provider signs in without leaving the document', async () => {
    authService.isAuthenticated.mockReturnValue(true);
    await render('acme/orders');

    fireEvent.click(screen.getByRole('button', { name: /sign in again/i }));
    await vi.waitFor(() => expect(router.navigateByUrl).toHaveBeenCalledWith('/acme/orders'));
  });

  it('goes back to the start page without touching the session', async () => {
    await render('acme/orders');

    fireEvent.click(screen.getByRole('button', { name: /start page/i }));

    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
    expect(authService.clearSession).not.toHaveBeenCalled();
  });
});
