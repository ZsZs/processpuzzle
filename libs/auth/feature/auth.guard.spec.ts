import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { authGuard } from './auth.guard';
import { AUTHENTICATION_SERVICE, AuthService } from '@processpuzzle/auth/domain';

describe('authGuard', () => {
  let mockRouter: jest.Mocked<Router>;
  let mockAuthService: jest.Mocked<AuthService>;
  let mockSnackBar: jest.Mocked<MatSnackBar>;
  let mockRoute: ActivatedRouteSnapshot;

  beforeEach(() => {
    mockRouter = {
      navigate: jest.fn().mockResolvedValue(true),
    } as unknown as jest.Mocked<Router>;

    mockAuthService = {
      authenticate: jest.fn().mockResolvedValue(true),
      isAuthenticated: jest.fn(),
    } as unknown as jest.Mocked<AuthService>;

    mockSnackBar = {
      open: jest.fn(),
    } as unknown as jest.Mocked<MatSnackBar>;

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

    jest.clearAllMocks();
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
});
