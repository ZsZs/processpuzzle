import { beforeEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { screen } from '@testing-library/angular';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { provideRouter, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RegistrationService } from '@processpuzzle/auth/domain';
import { mockLanguageConfig, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';

import { RegistrationComponent } from './registration.component';
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
  const registrationService = { register: vi.fn() };
  let router: { navigateByUrl: ReturnType<typeof vi.spyOn> };
  let component: RegistrationComponent;

  const setFormValue = (email: string, password: string, confirmPassword: string) => {
    component.registerForm.setValue({ email, password, confirmPassword });
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    registrationService.register.mockResolvedValue(undefined);
    const result = await setUpTranslocoTestBed(RegistrationComponent, testConfig, {
      imports: [ReactiveFormsModule],
      providers: [
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        provideRouter([]),
        { provide: MatSnackBar, useValue: snackBar },
        { provide: RegistrationService, useValue: registrationService },
        { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig },
      ],
    });
    component = result.component;
    router = { navigateByUrl: vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true) };
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
  it('should not register when the form is invalid', async () => {
    await component.onSubmit();
    expect(registrationService.register).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
  // endregion

  // region onSubmit - success
  it('should register with email and password, then go to the login page', async () => {
    setFormValue('user@example.com', 'Password123!', 'Password123!');

    await component.onSubmit();

    expect(registrationService.register).toHaveBeenCalledWith({ email: 'user@example.com', password: 'Password123!' });
    expect(snackBar.open).toHaveBeenCalledWith('Your account was created. Please sign in.', 'Close', { duration: 5000 });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/auth/login');
    expect(component.isLoading()).toBe(false);
  });

  it('should set isLoading to true while the registration is pending', async () => {
    let resolveRegistration: (() => void) | undefined;
    registrationService.register.mockImplementation(() => new Promise<void>((resolve) => (resolveRegistration = resolve)));
    setFormValue('user@example.com', 'Password123!', 'Password123!');

    const submitPromise = component.onSubmit();
    expect(component.isLoading()).toBe(true);

    resolveRegistration?.();
    await submitPromise;
    expect(component.isLoading()).toBe(false);
  });
  // endregion

  // region onSubmit - error handling
  const errorCodeCases: Array<{ code: string; expected: string }> = [
    { code: 'user.already-exists', expected: 'This email address is already registered' },
    { code: 'registration.password-policy', expected: 'Please choose a stronger password' },
    { code: 'registration.disabled', expected: 'Registration is not enabled' },
    { code: 'registration.rate-limited', expected: 'Too many attempts. Please try again later' },
    { code: 'registration.unreachable', expected: 'Registration is temporarily unavailable. Please try again later' },
    { code: 'user-directory.unavailable', expected: 'Registration is temporarily unavailable. Please try again later' },
    { code: 'something-else', expected: 'An error occurred during registration. Please try again.' },
  ];

  errorCodeCases.forEach(({ code, expected }) => {
    it(`should show "${expected}" when registration is refused with ${code}`, async () => {
      registrationService.register.mockRejectedValueOnce({ code });
      setFormValue('user@example.com', 'Password123!', 'Password123!');

      await component.onSubmit();

      expect(snackBar.open).toHaveBeenCalledWith(expected, 'Close', { duration: 5000, panelClass: ['error-snackbar'] });
    });
  });

  it('should show the generic message when the error carries no code', async () => {
    registrationService.register.mockRejectedValueOnce(new Error('boom'));
    setFormValue('user@example.com', 'Password123!', 'Password123!');

    await component.onSubmit();

    expect(snackBar.open).toHaveBeenCalledWith('An error occurred during registration. Please try again.', 'Close', expect.any(Object));
  });

  it('should stay on the page and stop loading after a failed submit', async () => {
    registrationService.register.mockRejectedValueOnce({ code: 'user.already-exists' });
    setFormValue('user@example.com', 'Password123!', 'Password123!');

    await component.onSubmit();

    expect(component.isLoading()).toBe(false);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
  // endregion
});
