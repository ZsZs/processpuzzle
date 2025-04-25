import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';
import { inject, Signal } from '@angular/core';
import { User } from '@angular/fire/auth';

// Mock the inject function and other Angular dependencies
jest.mock('@angular/core', () => ({
  inject: jest.fn(),
  Signal: jest.fn(),
  Injectable: () => () => ({}),
  computed: jest.fn(),
}));

// Mock the Angular router
jest.mock('@angular/router', () => ({
  Router: jest.fn(),
}));

// Mock the Angular fire auth
jest.mock('@angular/fire/auth', () => ({
  User: jest.fn(),
  Auth: jest.fn(),
  authState: jest.fn(),
}));

// Mock the rxjs-interop
jest.mock('@angular/core/rxjs-interop', () => ({
  toSignal: jest.fn(),
}));

describe('authGuard', () => {
  let mockRouter: jest.Mocked<Router>;
  let mockAuthService: Partial<AuthService>;
  let userValue: unknown = null;

  beforeEach(() => {
    mockRouter = {
      navigate: jest.fn().mockResolvedValue(true),
    } as unknown as jest.Mocked<Router>;

    mockAuthService = {
      user: Object.assign(() => userValue, { set: jest.fn(), update: jest.fn(), asReadonly: jest.fn() }) as unknown as Signal<User | null>,
    };

    (inject as jest.Mock).mockImplementation((token) => {
      if (token === Router) return mockRouter;
      if (token === AuthService) return mockAuthService;
      return null;
    });

    jest.clearAllMocks();
  });

  it('should return true when user is authenticated', async () => {
    userValue = { uid: '123' };
    const result = await authGuard();
    expect(result).toBe(true);
    expect(mockRouter.navigate).not.toHaveBeenCalled();
  });

  it('should navigate to login and return false when user is not authenticated', async () => {
    userValue = null;
    const result = await authGuard();
    expect(result).toBe(false);
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/auth/login']);
  });
});
