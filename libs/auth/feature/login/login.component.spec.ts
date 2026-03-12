import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { fireEvent, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { LoginComponent } from './login.component';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { provideRouter, Router } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AUTHENTICATION_SERVICE, AuthService } from '@processpuzzle/auth/domain';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { setupMockAuthService } from '../../test-setup';
import authDe from '../assets/i18n/auth/de.json';
import authEn from '../assets/i18n/auth/en.json';

const testConfig: TranslocoTestConfig = {
  scope: 'auth',
  translations: {
    'auth/en': authEn,
    'auth/de': authDe,
  },
};

describe.skip('LoginComponent', () => {
  let authService: AuthService;
  let fixture: ComponentFixture<LoginComponent>;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(LoginComponent, testConfig, {
      imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatIconModule, MatButtonModule, TranslocoDirective, TranslocoPipe],
      providers: [provideNoopAnimations(), provideRouter([]), { provide: AUTHENTICATION_SERVICE, useValue: setupMockAuthService() }],
    });

    authService = TestBed.inject(AUTHENTICATION_SERVICE);
    //    component = result.component;
    fixture = result.fixture;
  });

  it('should render login form', async () => {
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^sign in$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in with google/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });

  it('should show validation errors for empty form submission', async () => {
    // Get the form element
    const form = screen.getByRole('form', { name: 'Login Form' });

    // Get the email and password inputs
    const emailInput = screen.getByLabelText(/email/i);
    const passwordInput = screen.getByLabelText('Password');

    // Touch the inputs to trigger validation
    fireEvent.blur(emailInput);
    fireEvent.blur(passwordInput);

    // Submit the form
    fireEvent.submit(form);

    // Force change detection
    fixture.detectChanges();

    // Check for validation errors in the DOM
    const formElement = fixture.nativeElement;
    expect(formElement.textContent).toContain('Email is required');
    expect(formElement.textContent).toContain('Password is required');
  });

  it('should show validation error for invalid email', async () => {
    const emailInput = screen.getByLabelText(/email/i);
    await userEvent.type(emailInput, 'invalidemail');
    fireEvent.blur(emailInput);

    // Force change detection
    fixture.detectChanges();

    // Check for validation error in the DOM
    const formElement = fixture.nativeElement;
    expect(formElement.textContent).toContain('Please enter a valid email');
  });

  it('should toggle password visibility', async () => {
    const passwordInput = screen.getByLabelText('Password');
    expect(passwordInput).toHaveAttribute('type', 'password');

    // Find the visibility toggle button by finding the button that contains the mat-icon
    //    const visibilityIcon = screen.getByText(/visibility_off/i).closest('button');
    const visibilityIcon = screen.getByRole('button', { name: /toggle password visibility/i });

    if (visibilityIcon) fireEvent.click(visibilityIcon);

    // Force change detection
    fixture.detectChanges();

    expect(passwordInput).toHaveAttribute('type', 'text');

    // Now the icon should have changed to 'visibility'
    if (visibilityIcon) fireEvent.click(visibilityIcon);

    // Force change detection
    fixture.detectChanges();

    expect(passwordInput).toHaveAttribute('type', 'password');
  });

  it('should sign in successfully with valid credentials', async () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');

    await userEvent.type(screen.getByLabelText(/email/i), 'test@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(authService).toHaveBeenCalledWith(expect.anything(), 'test@example.com', 'Password123!');
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });

  it('should sign in with Google', async () => {
    // const mockSignInWithPopup = signInWithPopup as Mocked<any>;
    // mockSignInWithPopup.mockResolvedValueOnce({});

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');

    await userEvent.click(screen.getByRole('button', { name: /sign in with google/i }));

    expect(authService.login()).toHaveBeenCalled();
    //    expect(GoogleAuthProvider).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });

  it('should show error message for invalid email', async () => {
    // const mockSignIn = signInWithEmailAndPassword as Mocked<any>;
    // mockSignIn.mockRejectedValueOnce({ code: 'auth/invalid-email' });
    await userEvent.type(screen.getByLabelText(/email/i), 'test@google.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(screen.getByText(/invalid email address/i)).toBeInTheDocument();
  });

  it('should show error message for user not found', async () => {
    // const mockSignIn = signInWithEmailAndPassword as Mocked<any>;
    // mockSignIn.mockRejectedValueOnce({ code: 'auth/user-not-found' });
    await userEvent.type(screen.getByLabelText(/email/i), 'nonexistent@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(screen.getByText(/no account found with this email/i)).toBeInTheDocument();
  });

  it('should show error message for wrong password', async () => {
    // const mockSignIn = signInWithEmailAndPassword as Mocked<any>;
    // mockSignIn.mockRejectedValueOnce({
    //   code: 'auth/wrong-password',
    // });
    await userEvent.type(screen.getByLabelText(/email/i), 'test@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'WrongPassword');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(screen.getByText(/invalid password/i)).toBeInTheDocument();
  });

  it('should show error message for disabled account', async () => {
    // const mockSignIn = signInWithEmailAndPassword as Mocked<any>;
    // mockSignIn.mockRejectedValueOnce({
    //   code: 'auth/user-disabled',
    // });
    await userEvent.type(screen.getByLabelText(/email/i), 'disabled@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(screen.getByText(/this account has been disabled/i)).toBeInTheDocument();
  });

  it('should show error message when popup is closed', async () => {
    // const mockSignInWithPopup = signInWithPopup as Mocked<any>;
    // mockSignInWithPopup.mockRejectedValueOnce({
    //   code: 'auth/popup-closed-by-user',
    // });
    await userEvent.click(screen.getByRole('button', { name: /sign in with google/i }));

    expect(screen.getByText(/sign-in popup was closed before completion/i)).toBeInTheDocument();
  });

  it('should show generic error message for unknown errors', async () => {
    // const mockSignIn = signInWithEmailAndPassword as Mocked<any>;
    // mockSignIn.mockRejectedValueOnce({
    //   code: 'auth/unknown-error',
    // });
    await userEvent.type(screen.getByLabelText(/email/i), 'test@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }));

    expect(screen.getByText(/an error occurred during sign in/i)).toBeInTheDocument();
  });

  it('should show loading state during sign in', async () => {
    // const mockSignIn = signInWithEmailAndPassword as Mocked<any>;
    // // Use a delayed promise to test loading state
    // mockSignIn.mockImplementationOnce(() => {
    //   return new Promise((resolve) => {
    //     setTimeout(() => resolve({}), 100);
    //   });
    // });

    await userEvent.type(screen.getByLabelText(/email/i), 'test@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Password123!');

    const signInButton = screen.getByRole('button', { name: /^sign in$/i });
    fireEvent.click(signInButton);

    expect(screen.getByRole('button', { name: /^signing in...$/i })).toBeInTheDocument();
  });

  it('should navigate to registration page when clicking create account', async () => {
    const createAccountButton = screen.getByRole('button', { name: /create account/i });
    expect(createAccountButton).toHaveAttribute('routerlink', '/auth/register');
  });
});
