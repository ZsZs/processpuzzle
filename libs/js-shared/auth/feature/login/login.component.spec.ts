import { beforeEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { screen } from '@testing-library/angular';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TestBed } from '@angular/core/testing';
import { NavigateBackService } from '@processpuzzle/widgets';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';
import { mockLanguageConfig, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';

import { LoginComponent } from './login.component';
import { mockAuthService, setupMockAuthService } from '../../test-setup';
import authDe from '../assets/i18n/auth/de.json';
import authEn from '../assets/i18n/auth/en.json';

const testConfig: TranslocoTestConfig = {
  scope: 'auth',
  translations: {
    'auth/en': authEn,
    'auth/de': authDe,
  },
};

describe('LoginComponent', () => {
  const routeStack: string[] = [];
  const navigateBack = { getRouteStack: vi.fn<() => string[]>(() => routeStack) };
  const activatedRouteStub: { snapshot: { data: Record<string, unknown> } } = { snapshot: { data: {} } };
  let component: LoginComponent;
  let router: Router;
  let navigateSpy: ReturnType<typeof vi.spyOn>;

  const renderWithRouteData = async (data: Record<string, unknown> = {}) => {
    activatedRouteStub.snapshot.data = data;
    const result = await setUpTranslocoTestBed(LoginComponent, testConfig, {
      imports: [ReactiveFormsModule],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: activatedRouteStub },
        { provide: NavigateBackService, useValue: navigateBack },
        { provide: AUTHENTICATION_SERVICE, useValue: setupMockAuthService() },
        { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig },
      ],
    });
    component = result.component;
    router = TestBed.inject(Router);
    navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    return result;
  };

  const setCredentials = (email: string, password: string) => {
    component.loginForm.setValue({ email, password });
  };

  beforeEach(() => {
    vi.clearAllMocks();
    routeStack.length = 0;
    activatedRouteStub.snapshot.data = {};
  });

  // region Rendering & DI
  it('should create the component', async () => {
    await renderWithRouteData();
    expect(component).toBeTruthy();
  });

  it('should render the login form', async () => {
    await renderWithRouteData();
    expect(screen.getByRole('form', { name: 'Login Form' })).toBeInTheDocument();
  });
  // endregion

  // region ngOnInit
  it('should build the login form when no signedInUser is on the route', async () => {
    await renderWithRouteData();
    expect(component.loginForm).toBeDefined();
    expect(component.loginForm.contains('email')).toBe(true);
    expect(component.loginForm.contains('password')).toBe(true);
  });

  it('should skip building the login form when route already has a signedInUser', async () => {
    await renderWithRouteData();
    activatedRouteStub.snapshot.data = { signedInUser: { id: '1' } };
    (component as unknown as { loginForm: unknown }).loginForm = undefined;

    component.ngOnInit();

    expect(component.loginForm).toBeUndefined();
  });
  // endregion

  // region Form state
  it('should expose default signal and flag values', async () => {
    await renderWithRouteData();
    expect(component.isLoading()).toBe(false);
    expect(component.errorMessage()).toBe('');
    expect(component.hidePassword).toBe(true);
  });

  it('should mark the form invalid when fields are empty and validate email format', async () => {
    await renderWithRouteData();
    expect(component.loginForm.invalid).toBe(true);
    expect(component.loginForm.get('email')?.hasError('required')).toBe(true);
    expect(component.loginForm.get('password')?.hasError('required')).toBe(true);

    component.loginForm.get('email')?.setValue('not-an-email');
    expect(component.loginForm.get('email')?.hasError('email')).toBe(true);
  });

  it('should become valid with proper credentials', async () => {
    await renderWithRouteData();
    setCredentials('user@example.com', 'pw');
    expect(component.loginForm.valid).toBe(true);
  });

  it('should toggle password visibility', async () => {
    await renderWithRouteData();
    component.hidePassword = !component.hidePassword;
    expect(component.hidePassword).toBe(false);
  });
  // endregion

  // region onSubmit - guard
  it('should not call authService.login when the form is invalid', async () => {
    await renderWithRouteData();
    await component.onSubmit();
    expect(mockAuthService.login).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();
  });
  // endregion

  // region onSubmit - success
  it('should call authService.login with the last route from the stack and navigate home', async () => {
    await renderWithRouteData();
    routeStack.push('/dashboard', '/settings');
    setCredentials('user@example.com', 'pw');

    await component.onSubmit();

    expect(navigateBack.getRouteStack).toHaveBeenCalledTimes(1);
    expect(mockAuthService.login).toHaveBeenCalledWith('/settings', 'user@example.com', 'pw');
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
    expect(component.isLoading()).toBe(false);
    expect(component.errorMessage()).toBe('');
  });

  it('should pass undefined as the redirect url when the route stack is empty', async () => {
    await renderWithRouteData();
    setCredentials('user@example.com', 'pw');

    await component.onSubmit();

    expect(mockAuthService.login).toHaveBeenCalledWith(undefined, 'user@example.com', 'pw');
  });

  it('should set isLoading while the login call is pending', async () => {
    await renderWithRouteData();
    let resolveLogin: (() => void) | undefined;
    mockAuthService.login = vi.fn().mockImplementation(
      () => new Promise<void>((resolve) => (resolveLogin = resolve)),
    );
    setCredentials('user@example.com', 'pw');

    const pending = component.onSubmit();
    expect(component.isLoading()).toBe(true);

    resolveLogin?.();
    await pending;
    expect(component.isLoading()).toBe(false);
  });
  // endregion

  // region onSubmit - error mapping
  const errorCases: Array<{ name: string; code: string; expected: string }> = [
    { name: 'invalid-email', code: 'auth/invalid-email', expected: 'Invalid email address' },
    { name: 'user-disabled', code: 'auth/user-disabled', expected: 'This account has been disabled' },
    { name: 'user-not-found', code: 'auth/user-not-found', expected: 'No account found with this email' },
    { name: 'wrong-password', code: 'auth/wrong-password', expected: 'Invalid password' },
    { name: 'popup-closed-by-user', code: 'auth/popup-closed-by-user', expected: 'Sign-in popup was closed before completion' },
    { name: 'unknown-code', code: 'auth/something-else', expected: 'An error occurred during sign in' },
  ];

  errorCases.forEach(({ name, code, expected }) => {
    it(`should set errorMessage to "${expected}" when login rejects with ${name}`, async () => {
      await renderWithRouteData();
      mockAuthService.login = vi.fn().mockRejectedValueOnce({ code });
      setCredentials('user@example.com', 'pw');

      await component.onSubmit();

      expect(component.errorMessage()).toBe(expected);
      expect(component.isLoading()).toBe(false);
      expect(navigateSpy).not.toHaveBeenCalled();
    });
  });

  it('should fall back to the error message property when no code is present', async () => {
    await renderWithRouteData();
    mockAuthService.login = vi.fn().mockRejectedValueOnce({ message: 'some message' });
    setCredentials('user@example.com', 'pw');

    await component.onSubmit();

    expect(component.errorMessage()).toBe('An error occurred during sign in');
  });

  it('should show the default error when neither code nor message is provided', async () => {
    await renderWithRouteData();
    mockAuthService.login = vi.fn().mockRejectedValueOnce({});
    setCredentials('user@example.com', 'pw');

    await component.onSubmit();

    expect(component.errorMessage()).toBe('An error occurred during sign in');
  });
  // endregion

  // region signInWithGoogle
  it('should navigate home when sign-in with Google succeeds', async () => {
    await renderWithRouteData();
    await (component as unknown as { signInWithGoogle: () => Promise<void> }).signInWithGoogle();

    expect(navigateSpy).toHaveBeenCalledWith(['/']);
    expect(component.errorMessage()).toBe('');
    expect(component.isLoading()).toBe(false);
  });

  it('should map errors from the router into errorMessage when sign-in with Google fails', async () => {
    await renderWithRouteData();
    navigateSpy.mockRejectedValueOnce({ code: 'auth/popup-closed-by-user' });

    await (component as unknown as { signInWithGoogle: () => Promise<void> }).signInWithGoogle();

    expect(component.errorMessage()).toBe('Sign-in popup was closed before completion');
    expect(component.isLoading()).toBe(false);
  });
  // endregion
});
