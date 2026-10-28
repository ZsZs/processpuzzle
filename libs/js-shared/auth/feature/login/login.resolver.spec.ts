import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, ResolveFn, RouterStateSnapshot } from '@angular/router';
import { AUTHENTICATION_SERVICE, User } from '@processpuzzle/auth/domain';
import { NavigateBackService } from '@processpuzzle/util';
import { loginResolver } from './login.resolver';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('loginResolver', () => {
  let mockAuthService: { login: ReturnType<typeof vi.fn> };
  let mockNavigateBackService: { getRouteStack: ReturnType<typeof vi.fn> };
  const executeResolver: ResolveFn<User | undefined> = (...resolverParameters) => TestBed.runInInjectionContext(() => loginResolver(...resolverParameters));

  beforeEach(() => {
    mockAuthService = { login: vi.fn().mockResolvedValue(undefined) };
    mockNavigateBackService = { getRouteStack: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: AUTHENTICATION_SERVICE, useValue: mockAuthService },
        { provide: NavigateBackService, useValue: mockNavigateBackService },
      ],
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('should call authService.login with the last route from route stack', async () => {
    const mockUser: User = new User('test@example.com', '123', 'Test', 'User');
    const mockRouteStack = ['/dashboard', '/profile', '/settings'];

    mockNavigateBackService.getRouteStack.mockReturnValue([...mockRouteStack]);
    mockAuthService.login.mockResolvedValue(mockUser);

    const result = await executeResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

    expect(mockNavigateBackService.getRouteStack).toHaveBeenCalledTimes(1);
    expect(mockAuthService.login).toHaveBeenCalledWith('/settings');
    expect(result).toBe(mockUser);
  });

  it('should handle empty route stack gracefully', async () => {
    const mockUser: User = new User('user@example.com', '456', 'Another', 'User');

    mockNavigateBackService.getRouteStack.mockReturnValue([]);
    mockAuthService.login.mockResolvedValue(mockUser);

    const result = await executeResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

    expect(mockNavigateBackService.getRouteStack).toHaveBeenCalledTimes(1);
    expect(mockAuthService.login).toHaveBeenCalledWith(undefined);
    expect(result).toBe(mockUser);
  });

  it('should return undefined when authService.login returns undefined', async () => {
    mockNavigateBackService.getRouteStack.mockReturnValue(['/home']);
    mockAuthService.login.mockResolvedValue(undefined);

    const result = await executeResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

    expect(mockAuthService.login).toHaveBeenCalledWith('/home');
    expect(result).toBeUndefined();
  });

  it('should propagate errors from authService.login', async () => {
    const mockError = new Error('Authentication failed');

    mockNavigateBackService.getRouteStack.mockReturnValue(['/protected']);
    mockAuthService.login.mockRejectedValue(mockError);

    await expect(executeResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)).rejects.toThrow('Authentication failed');
    expect(mockAuthService.login).toHaveBeenCalledWith('/protected');
  });
});
