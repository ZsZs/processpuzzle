import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { authGuard } from './auth.guard';
import { AUTHENTICATION_SERVICE, AuthService } from '@processpuzzle/auth/domain';
import { beforeEach, describe, expect, it, Mocked, vi } from 'vitest';

describe('authGuard', () => {
  let mockRouter: Mocked<Router>;
  let mockAuthService: Mocked<AuthService>;
  let mockSnackBar: Mocked<MatSnackBar>;
  let mockRoute: ActivatedRouteSnapshot;

  beforeEach(() => {
    mockRouter = {
      navigate: vi.fn().mockResolvedValue(true),
    } as unknown as Mocked<Router>;

    mockAuthService = {
      authenticate: vi.fn().mockResolvedValue(true),
      isAuthenticated: vi.fn(),
    } as unknown as Mocked<AuthService>;

    mockSnackBar = {
      open: vi.fn(),
    } as unknown as Mocked<MatSnackBar>;

    mockRoute = {
      routeConfig: { path: '' },
    } as unknown as ActivatedRouteSnapshot;

    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: mockRouter },
        { provide: AUTHENTICATION_SERVICE, useValue: mockAuthService },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    });

    vi.clearAllMocks();
  });

  it('should return true when user is authenticated for protected routes', async () => {
    mockAuthService.isAuthenticated.mockReturnValue(true);
    const result = await TestBed.runInInjectionContext(() => authGuard(mockRoute));
    expect(result).toBe(true);
    expect(mockAuthService.authenticate).toHaveBeenCalled();
    expect(mockRouter.navigate).not.toHaveBeenCalled();
  });

  it('should navigate to login and return false when user is not authenticated', async () => {
    mockAuthService.isAuthenticated.mockReturnValue(false);
    const result = await TestBed.runInInjectionContext(() => authGuard(mockRoute));
    expect(result).toBe(false);
    expect(mockAuthService.authenticate).toHaveBeenCalled();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/auth/login']);
  });

  it('should allow access to login page when user is not authenticated', async () => {
    const loginRoute = { routeConfig: { path: 'login' } } as unknown as ActivatedRouteSnapshot;
    mockAuthService.isAuthenticated.mockReturnValue(false);
    const result = await TestBed.runInInjectionContext(() => authGuard(loginRoute));
    expect(result).toBe(true);
    expect(mockAuthService.authenticate).toHaveBeenCalled();
    expect(mockRouter.navigate).not.toHaveBeenCalled();
  });

  it('should show snackbar and redirect to home when accessing login page while already authenticated', async () => {
    const loginRoute = { routeConfig: { path: 'login' } } as unknown as ActivatedRouteSnapshot;
    mockAuthService.isAuthenticated.mockReturnValue(true);
    const result = await TestBed.runInInjectionContext(() => authGuard(loginRoute));
    expect(result).toBe(false);
    expect(mockAuthService.authenticate).toHaveBeenCalled();
    expect(mockSnackBar.open).toHaveBeenCalledWith('You are already logged in', 'Close', { duration: 3000 });
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/']);
  });

  it('should allow access to logout page when user is authenticated', async () => {
    const logoutRoute = { routeConfig: { path: 'logout' } } as unknown as ActivatedRouteSnapshot;
    mockAuthService.isAuthenticated.mockReturnValue(true);
    const result = await TestBed.runInInjectionContext(() => authGuard(logoutRoute));
    expect(result).toBe(true);
    expect(mockAuthService.authenticate).toHaveBeenCalled();
    expect(mockRouter.navigate).not.toHaveBeenCalled();
  });

  it('should show snackbar and redirect to home when accessing logout page while not authenticated', async () => {
    const logoutRoute = { routeConfig: { path: 'logout' } } as unknown as ActivatedRouteSnapshot;
    mockAuthService.isAuthenticated.mockReturnValue(false);
    const result = await TestBed.runInInjectionContext(() => authGuard(logoutRoute));
    expect(result).toBe(false);
    expect(mockAuthService.authenticate).toHaveBeenCalled();
    expect(mockSnackBar.open).toHaveBeenCalledWith('You are not already logged in', 'Close', { duration: 3000 });
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/']);
  });
});
