// Mock the domain module before any imports to avoid keycloak dependencies
jest.mock('@processpuzzle/auth/domain', () => {
  const actual = jest.requireActual('@angular/core');
  return {
    AUTHENTICATION_SERVICE: new actual.InjectionToken('AUTHENTICATION_SERVICE'),
  };
});

// Mock the inject function
jest.mock('@angular/core', () => {
  const actual = jest.requireActual('@angular/core');
  return {
    ...actual,
    inject: jest.fn(),
  };
});

import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { authGuard } from './auth.guard';
import { inject } from '@angular/core';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';

describe('authGuard', () => {
  let mockRouter: jest.Mocked<Router>;
  let mockAuthService: any;
  let mockSnackBar: jest.Mocked<MatSnackBar>;
  let mockRoute: ActivatedRouteSnapshot;

  beforeEach(() => {
    mockRouter = {
      navigate: jest.fn().mockResolvedValue(true),
    } as unknown as jest.Mocked<Router>;

    mockAuthService = {
      authenticate: jest.fn().mockResolvedValue(true),
      isAuthenticated: jest.fn(),
    };

    mockSnackBar = {
      open: jest.fn(),
    } as unknown as jest.Mocked<MatSnackBar>;

    mockRoute = {
      routeConfig: { path: '' },
    } as unknown as ActivatedRouteSnapshot;

    (inject as jest.Mock).mockImplementation((token) => {
      if (token === Router) return mockRouter;
      if (token === AUTHENTICATION_SERVICE) return mockAuthService;
      if (token === MatSnackBar) return mockSnackBar;
      return null;
    });

    jest.clearAllMocks();
  });

  it('should return true when user is authenticated for protected routes', async () => {
    mockAuthService.isAuthenticated.mockReturnValue(true);
    const result = await authGuard(mockRoute);
    expect(result).toBe(true);
    expect(mockAuthService.authenticate).toHaveBeenCalled();
    expect(mockRouter.navigate).not.toHaveBeenCalled();
  });

  it('should navigate to login and return false when user is not authenticated', async () => {
    mockAuthService.isAuthenticated.mockReturnValue(false);
    const result = await authGuard(mockRoute);
    expect(result).toBe(false);
    expect(mockAuthService.authenticate).toHaveBeenCalled();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/auth/login']);
  });
});
