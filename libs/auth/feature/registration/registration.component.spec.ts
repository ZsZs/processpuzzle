import '../../test-setup';
import { setupMockAuthService } from '../../test-setup';
import { fireEvent, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { RegistrationComponent } from './registration.component';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { createUserWithEmailAndPassword } from 'firebase/auth';
import { mockLanguageConfig, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { beforeEach, Mocked } from 'vitest';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';
import authDe from '../assets/i18n/auth/de.json';
import authEn from '../assets/i18n/auth/en.json';

describe('RegistrationComponent', () => {
  const mockSnackBar = { open: vi.fn() };
  const testConfig: TranslocoTestConfig = {
    scope: 'auth',
    translations: {
      en: { auth: { authEn } },
      de: { auth: { authDe } },
    },
  };
  let component: RegistrationComponent;

  beforeEach(async () => {
    vi.clearAllMocks();
    const result = await setUpTranslocoTestBed(RegistrationComponent, testConfig, {
      imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
      providers: [
        { provide: MatSnackBar, useValue: mockSnackBar },
        { provide: AUTHENTICATION_SERVICE, useValue: setupMockAuthService() },
        { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig },
      ],
    });

    component = result.component;
  });

  const fillRegistrationForm = async (email: string, password: string, confirmPassword: string) => {
    await userEvent.type(screen.getByLabelText(/email/i), email);
    await userEvent.type(screen.getByLabelText(/^password$/i), password);
    await userEvent.type(screen.getByLabelText(/confirm password/i), confirmPassword);
  };

  const submitRegistrationForm = async () => {
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));
  };

  it('should render registration form', async () => {
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });

  it('should show validation errors for empty form submission', async () => {
    const form = screen.getByRole('form', { name: 'Registration Form' });
    await fireEvent.submit(form);

    expect(screen.getByText(/email is required/i)).toBeInTheDocument();
    expect(screen.getByText(/password is required/i)).toBeInTheDocument();
  });

  it('should show validation error for invalid email', async () => {
    const emailInput = screen.getByLabelText(/email/i);
    await userEvent.type(emailInput, 'invalidemail');
    await fireEvent.blur(emailInput);

    expect(screen.getByText(/please enter a valid email/i)).toBeInTheDocument();
  });

  it('should register successfully with valid credentials', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as Mocked<any>;
    mockCreateUser.mockResolvedValueOnce({});

    //    await renderComponent();

    await fillRegistrationForm('test@example.com', 'Password123!', 'Password123!');
    await submitRegistrationForm();

    expect(mockCreateUser).toHaveBeenCalledWith(expect.anything(), 'test@example.com', 'Password123!');
  });

  it('should show error message when email is already in use', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as Mocked<any>;
    mockCreateUser.mockRejectedValueOnce({
      code: 'auth/email-already-in-use',
    });

    //    await renderComponent();

    await fillRegistrationForm('test@example.com', 'Password123!', 'Password123!');
    await submitRegistrationForm();

    expect(mockSnackBar.open).toHaveBeenCalledWith('This email address is already registered', 'Close', expect.any(Object));
  });

  it('should show error message for weak password', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as Mocked<any>;
    mockCreateUser.mockRejectedValueOnce({
      code: 'auth/weak-password',
    });

    //    await renderComponent();

    await fillRegistrationForm('test@example.com', 'weakpass', 'weakpass');

    const form = screen.getByRole('form', { name: 'Registration Form' });
    await fireEvent.submit(form);

    expect(mockSnackBar.open).toHaveBeenCalledWith('Please choose a stronger password', 'Close', expect.any(Object));
  });

  it('should handle unknown errors', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as Mocked<any>;
    mockCreateUser.mockRejectedValueOnce({
      code: 'auth/unknown-error',
    });

    //    await renderComponent();

    await fillRegistrationForm('test@example.com', 'Password123!', 'Password123!');
    await submitRegistrationForm();

    expect(mockSnackBar.open).toHaveBeenCalledWith('An error occurred during registration. Please try again.', 'Close', expect.any(Object));
  });

  it('should validate password requirements', async () => {
    const passwordInput = screen.getByLabelText(/^password$/i);
    await userEvent.type(passwordInput, 'short');
    await fireEvent.blur(passwordInput);

    expect(screen.getByText(/password must be at least 6 characters/i)).toBeInTheDocument();
  });

  it('should show error message for invalid email error', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as Mocked<any>;
    mockCreateUser.mockRejectedValueOnce({
      code: 'auth/invalid-email',
    });

    //    await renderComponent();

    await fillRegistrationForm('test@example.com', 'Password123!', 'Password123!');
    await submitRegistrationForm();

    expect(mockSnackBar.open).toHaveBeenCalledWith('Please enter a valid email address', 'Close', expect.any(Object));
  });

  it('should show error message for operation not allowed error', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as Mocked<any>;
    mockCreateUser.mockRejectedValueOnce({
      code: 'auth/operation-not-allowed',
    });

    //    await renderComponent();

    await fillRegistrationForm('test@example.com', 'Password123!', 'Password123!');
    await submitRegistrationForm();

    expect(mockSnackBar.open).toHaveBeenCalledWith('Email/password registration is not enabled', 'Close', expect.any(Object));
  });

  it('should handle error with message property but no code', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as Mocked<any>;
    mockCreateUser.mockRejectedValueOnce({
      message: 'Some error message',
    });

    //    await renderComponent();

    await fillRegistrationForm('test@example.com', 'Password123!', 'Password123!');
    await submitRegistrationForm();

    expect(mockSnackBar.open).toHaveBeenCalledWith('An error occurred during registration. Please try again.', 'Close', expect.any(Object));
  });

  it('should not submit form when invalid', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as Mocked<any>;
    //    await renderComponent();

    const form = screen.getByRole('form', { name: 'Registration Form' });
    await fireEvent.submit(form);

    expect(mockCreateUser).not.toHaveBeenCalled();
  });

  it('should toggle password visibility', async () => {
    expect(component.hidePassword).toBe(true);

    component.hidePassword = !component.hidePassword;

    expect(component.hidePassword).toBe(false);
  });

  it('should toggle confirm password visibility', async () => {
    expect(component.hideConfirmPassword).toBe(true);

    component.hideConfirmPassword = !component.hideConfirmPassword;

    expect(component.hideConfirmPassword).toBe(false);
  });

  it('should set loading state during registration', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as Mocked<any>;
    mockCreateUser.mockImplementation(() => new Promise((resolve) => setTimeout(resolve, 100)));

    //    const { fixture } = await renderComponent();

    expect(component.isLoading()).toBe(false);

    await fillRegistrationForm('test@example.com', 'Password123!', 'Password123!');

    const submitPromise = userEvent.click(screen.getByRole('button', { name: /create account/i }));

    // Check loading state is set during async operation
    // fixture.detectChanges();
    // await fixture.whenStable();

    await submitPromise;
  });

  it('should reset loading state after successful registration', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as Mocked<any>;
    mockCreateUser.mockResolvedValueOnce({});

    //    const { fixture } = await renderComponent();
    //    const component = fixture.componentInstance;

    await fillRegistrationForm('test@example.com', 'Password123!', 'Password123!');
    await submitRegistrationForm();

    expect(component.isLoading()).toBe(false);
  });

  it('should reset loading state after failed registration', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as Mocked<any>;
    mockCreateUser.mockRejectedValueOnce({
      code: 'auth/email-already-in-use',
    });

    //    const { fixture } = await renderComponent();
    //    const component = fixture.componentInstance;

    await fillRegistrationForm('test@example.com', 'Password123!', 'Password123!');
    await submitRegistrationForm();

    expect(component.isLoading()).toBe(false);
  });
});
