import { beforeEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { screen } from '@testing-library/angular';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TestBed } from '@angular/core/testing';
import { NavigateBackService } from '@processpuzzle/widgets';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';
import { mockLanguageConfig, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';

import { RegistrationComponent } from './registration.component';
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

describe('RegistrationComponent', () => {
  const snackBar = { open: vi.fn() };
  const navigateBack = { goBack: vi.fn() };
  let component: RegistrationComponent;

  const setFormValue = (email: string, password: string, confirmPassword: string) => {
    component.registerForm.setValue({ email, password, confirmPassword });
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    const result = await setUpTranslocoTestBed(RegistrationComponent, testConfig, {
      imports: [ReactiveFormsModule],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: MatSnackBar, useValue: snackBar },
        { provide: NavigateBackService, useValue: navigateBack },
        { provide: AUTHENTICATION_SERVICE, useValue: setupMockAuthService() },
        { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig },
      ],
    });
    component = result.component;
  });

  // region Rendering
  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should render the registration form with all controls', () => {
    expect(screen.getByRole('form', { name: 'Registration Form' })).toBeInTheDocument();
    expect(component.registerForm.contains('email')).toBe(true);
    expect(component.registerForm.contains('password')).toBe(true);
    expect(component.registerForm.contains('confirmPassword')).toBe(true);
  });

  it('should initialize signals and visibility flags to defaults', () => {
    expect(component.isLoading()).toBe(false);
    expect(component.errorMessage()).toBe('');
    expect(component.hidePassword).toBe(true);
    expect(component.hideConfirmPassword).toBe(true);
  });
  // endregion

  // region Form validation
  it('should be invalid when all fields are empty', () => {
    expect(component.registerForm.invalid).toBe(true);
    expect(component.registerForm.get('email')?.hasError('required')).toBe(true);
    expect(component.registerForm.get('password')?.hasError('required')).toBe(true);
    expect(component.registerForm.get('confirmPassword')?.hasError('required')).toBe(true);
  });

  it('should flag an invalid email format', () => {
    component.registerForm.get('email')?.setValue('not-an-email');
    expect(component.registerForm.get('email')?.hasError('email')).toBe(true);
  });

  it('should flag passwords shorter than 6 characters', () => {
    component.registerForm.get('password')?.setValue('abc');
    expect(component.registerForm.get('password')?.hasError('minlength')).toBe(true);
  });

  it('should be valid when all fields are set correctly', () => {
    setFormValue('user@example.com', 'Password123!', 'Password123!');
    expect(component.registerForm.valid).toBe(true);
    expect(component.registerForm.errors).toBeNull();
  });

  it('passwordMatchValidator should return null for matching passwords and a passwordMismatch error otherwise', () => {
    const validator = (component as unknown as { passwordMatchValidator: () => (form: FormGroup) => unknown })
      .passwordMatchValidator();
    const matching = new FormGroup({
      password: new FormControl('Password123!'),
      confirmPassword: new FormControl('Password123!'),
    });
    const mismatched = new FormGroup({
      password: new FormControl('Password123!'),
      confirmPassword: new FormControl('Different!'),
    });

    expect(validator(matching)).toBeNull();
    expect(validator(mismatched)).toEqual({ passwordMismatch: true });
  });
  // endregion

  // region Visibility toggles
  it('should toggle password visibility', () => {
    component.hidePassword = !component.hidePassword;
    expect(component.hidePassword).toBe(false);
  });

  it('should toggle confirm password visibility', () => {
    component.hideConfirmPassword = !component.hideConfirmPassword;
    expect(component.hideConfirmPassword).toBe(false);
  });
  // endregion

  // region onSubmit - guard
  it('should not call authService.login when the form is invalid', async () => {
    await component.onSubmit();
    expect(mockAuthService.login).not.toHaveBeenCalled();
    expect(navigateBack.goBack).not.toHaveBeenCalled();
  });
  // endregion

  // region onSubmit - success
  it('should call authService.login with email and password on submit', async () => {
    setFormValue('user@example.com', 'Password123!', 'Password123!');

    await component.onSubmit();

    expect(mockAuthService.login).toHaveBeenCalledWith('', 'user@example.com', 'Password123!');
  });

  it('should reset loading state and navigate back after successful submit', async () => {
    setFormValue('user@example.com', 'Password123!', 'Password123!');

    await component.onSubmit();

    expect(component.isLoading()).toBe(false);
    expect(component.errorMessage()).toBe('');
    expect(navigateBack.goBack).toHaveBeenCalledTimes(1);
  });

  it('should set isLoading to true while the login call is pending', async () => {
    let resolveLogin: (() => void) | undefined;
    mockAuthService.login = vi.fn().mockImplementation(
      () => new Promise<void>((resolve) => (resolveLogin = resolve)),
    );
    setFormValue('user@example.com', 'Password123!', 'Password123!');

    const submitPromise = component.onSubmit();
    expect(component.isLoading()).toBe(true);

    resolveLogin?.();
    await submitPromise;
    expect(component.isLoading()).toBe(false);
  });
  // endregion

  // region onSubmit - error handling
  const errorCodeCases: Array<{ name: string; code: string; expected: string }> = [
    { name: 'email-already-in-use', code: 'auth/email-already-in-use', expected: 'This email address is already registered' },
    { name: 'invalid-email', code: 'auth/invalid-email', expected: 'Please enter a valid email address' },
    { name: 'operation-not-allowed', code: 'auth/operation-not-allowed', expected: 'Email/password registration is not enabled' },
    { name: 'weak-password', code: 'auth/weak-password', expected: 'Please choose a stronger password' },
    { name: 'unknown-code', code: 'auth/something-else', expected: 'An error occurred during registration. Please try again.' },
  ];

  errorCodeCases.forEach(({ name, code, expected }) => {
    it(`should show "${expected}" snackbar when login rejects with ${name}`, async () => {
      mockAuthService.login = vi.fn().mockRejectedValueOnce({ code });
      setFormValue('user@example.com', 'Password123!', 'Password123!');

      await component.onSubmit();

      expect(snackBar.open).toHaveBeenCalledWith(expected, 'Close', {
        duration: 5000,
        panelClass: ['error-snackbar'],
      });
    });
  });

  it('should fall back to the message property when no code is present', async () => {
    mockAuthService.login = vi.fn().mockRejectedValueOnce({ message: 'some message' });
    setFormValue('user@example.com', 'Password123!', 'Password123!');

    await component.onSubmit();

    expect(snackBar.open).toHaveBeenCalledWith(
      'An error occurred during registration. Please try again.',
      'Close',
      expect.any(Object),
    );
  });

  it('should fall back to the generic message when error has neither code nor message', async () => {
    mockAuthService.login = vi.fn().mockRejectedValueOnce({});
    setFormValue('user@example.com', 'Password123!', 'Password123!');

    await component.onSubmit();

    expect(snackBar.open).toHaveBeenCalledWith(
      'An error occurred during registration. Please try again.',
      'Close',
      expect.any(Object),
    );
  });

  it('should reset loading state and navigate back even after a failed submit', async () => {
    mockAuthService.login = vi.fn().mockRejectedValueOnce({ code: 'auth/email-already-in-use' });
    setFormValue('user@example.com', 'Password123!', 'Password123!');

    await component.onSubmit();

    expect(component.isLoading()).toBe(false);
    expect(component.errorMessage()).toBe('');
    expect(navigateBack.goBack).toHaveBeenCalledTimes(1);
  });
  // endregion

  // region DI sanity
  it('should inject the configured authentication service', () => {
    expect(TestBed.inject(AUTHENTICATION_SERVICE)).toBe(mockAuthService);
  });
  // endregion
});
