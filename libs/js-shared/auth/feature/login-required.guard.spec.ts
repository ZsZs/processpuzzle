import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AUTHENTICATION_SERVICE, AuthService } from '@processpuzzle/auth/domain';
import { beforeEach, describe, expect, it, Mocked, vi } from 'vitest';
import { loginRequiredGuard } from './login-required.guard';

describe('loginRequiredGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/acme/home' } as RouterStateSnapshot;
  const loginPage = {} as UrlTree;

  let authService: Mocked<AuthService>;
  let router: Mocked<Router>;

  beforeEach(() => {
    authService = {
      authenticate: vi.fn(),
      isAuthenticated: vi.fn().mockReturnValue(false),
      login: vi.fn().mockResolvedValue(undefined),
    } as unknown as Mocked<AuthService>;
    router = { createUrlTree: vi.fn().mockReturnValue(loginPage) } as unknown as Mocked<Router>;

    TestBed.configureTestingModule({
      providers: [
        { provide: AUTHENTICATION_SERVICE, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
  });

  const run = () => TestBed.runInInjectionContext(() => loginRequiredGuard(route, state));

  it('lets an authenticated user through without logging in again', async () => {
    authService.authenticate.mockResolvedValue(true);

    await expect(run()).resolves.toBe(true);
    expect(authService.login).not.toHaveBeenCalled();
  });

  it('asks the provider to log in and return to the refused URL, not to the application root', async () => {
    authService.authenticate.mockResolvedValue(false);

    await run();

    expect(authService.login).toHaveBeenCalledWith('/acme/home');
  });

  it('falls back to the login form when the provider has no hosted login page', async () => {
    authService.authenticate.mockResolvedValue(false);

    await expect(run()).resolves.toBe(loginPage);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/auth/login']);
  });
});
