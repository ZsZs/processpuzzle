import { fireEvent, render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { RegistrationComponent } from './registration.component';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { createUserWithEmailAndPassword } from 'firebase/auth';
import { Auth } from '@angular/fire/auth';
import { getTranslocoTestingModule } from '@processpuzzle/test-util';
import authDe from '../assets/i18n/auth/de.json';
import authEn from '../assets/i18n/auth/en.json';

// Mock Firebase auth
jest.mock('firebase/auth', () => ({
  createUserWithEmailAndPassword: jest.fn(),
}));

describe('RegistrationComponent', () => {
  const mockSnackBar = {
    open: jest.fn(),
  };

  const mockAuth = {
    createUserWithEmailAndPassword: jest.fn(),
  };

  const renderComponent = async () => {
    return render(RegistrationComponent, {
      imports: [getTranslocoTestingModule({ 'auth/de': authDe, 'auth/en': authEn }), ReactiveFormsModule, MatFormFieldModule, MatInputModule, BrowserAnimationsModule],
      providers: [
        { provide: MatSnackBar, useValue: mockSnackBar },
        { provide: Auth, useValue: mockAuth },
      ],
    });
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render registration form', async () => {
    await renderComponent();

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });

  it('should show validation errors for empty form submission', async () => {
    await renderComponent();

    const form = screen.getByRole('form', { name: 'Registration Form' });
    await fireEvent.submit(form);

    expect(screen.getByText(/email is required/i)).toBeInTheDocument();
    expect(screen.getByText(/password is required/i)).toBeInTheDocument();
  });

  it('should show validation error for invalid email', async () => {
    await renderComponent();

    const emailInput = screen.getByLabelText(/email/i);
    await userEvent.type(emailInput, 'invalidemail');
    await fireEvent.blur(emailInput);

    expect(screen.getByText(/please enter a valid email/i)).toBeInTheDocument();
  });

  it('should register successfully with valid credentials', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as jest.Mock;
    mockCreateUser.mockResolvedValueOnce({});

    await renderComponent();

    await userEvent.type(screen.getByLabelText(/email/i), 'test@example.com');
    await userEvent.type(screen.getByLabelText(/^password$/i), 'Password123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(mockCreateUser).toHaveBeenCalledWith(expect.anything(), 'test@example.com', 'Password123!');
  });

  it('should show error message when email is already in use', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as jest.Mock;
    mockCreateUser.mockRejectedValueOnce({
      code: 'auth/email-already-in-use',
    });

    await renderComponent();

    await userEvent.type(screen.getByLabelText(/email/i), 'test@example.com');
    await userEvent.type(screen.getByLabelText(/^password$/i), 'Password123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(mockSnackBar.open).toHaveBeenCalledWith('This email address is already registered', 'Close', expect.any(Object));
  });

  it('should show error message for weak password', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as jest.Mock;
    mockCreateUser.mockRejectedValueOnce({
      code: 'auth/weak-password',
    });

    await renderComponent();

    await userEvent.type(screen.getByLabelText(/email/i), 'test@example.com');
    await userEvent.type(screen.getByLabelText(/^password$/i), 'weakpass');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'weakpass');

    const form = screen.getByRole('form', { name: 'Registration Form' });
    await fireEvent.submit(form);

    expect(mockSnackBar.open).toHaveBeenCalledWith('Please choose a stronger password', 'Close', expect.any(Object));
  });

  it('should handle unknown errors', async () => {
    const mockCreateUser = createUserWithEmailAndPassword as jest.Mock;
    mockCreateUser.mockRejectedValueOnce({
      code: 'auth/unknown-error',
    });

    await renderComponent();

    await userEvent.type(screen.getByLabelText(/email/i), 'test@example.com');
    await userEvent.type(screen.getByLabelText(/^password$/i), 'Password123!');
    await userEvent.type(screen.getByLabelText(/confirm password/i), 'Password123!');
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(mockSnackBar.open).toHaveBeenCalledWith('An error occurred during registration. Please try again.', 'Close', expect.any(Object));
  });

  it('should validate password requirements', async () => {
    await renderComponent();

    const passwordInput = screen.getByLabelText(/^password$/i);
    await userEvent.type(passwordInput, 'short');
    await fireEvent.blur(passwordInput);

    expect(screen.getByText(/password must be at least 6 characters/i)).toBeInTheDocument();
  });
});
