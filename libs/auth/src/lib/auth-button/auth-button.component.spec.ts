import { fireEvent, render, screen } from '@testing-library/angular';
import { AuthButtonComponent } from './auth-button.component';
import { AuthService } from '../domain/auth.service';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { Signal, signal } from '@angular/core';

describe('AuthButtonComponent', () => {
  // Helper function to create a mock AuthService with controlled authentication state
  const createMockAuthService = (isAuth: boolean) => {
    return {
      isAuthenticated: signal(isAuth) as Signal<boolean>,
    };
  };

  // Helper function to render the component with different auth states
  const renderComponent = async (isAuthenticated: boolean) => {
    const mockAuthService = createMockAuthService(isAuthenticated);

    return render(AuthButtonComponent, {
      imports: [MatIconModule, MatButtonModule, MatMenu, MatMenuItem, MatMenuTrigger, RouterLink, BrowserAnimationsModule],
      providers: [{ provide: AuthService, useValue: mockAuthService }],
    });
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should create', async () => {
    await renderComponent(false);
    expect(screen.getByLabelText('Auth Button')).toBeInTheDocument();
  });

  it('should display login and register options when not authenticated', async () => {
    await renderComponent(false);

    // Click the auth button to open the menu
    await fireEvent.click(screen.getByLabelText('Auth Button'));

    // Check that login and register options are displayed
    expect(screen.getByText('Login')).toBeInTheDocument();
    expect(screen.getByText('Register')).toBeInTheDocument();

    // Check that authenticated options are not displayed
    expect(screen.queryByText('Personal Data')).not.toBeInTheDocument();
    expect(screen.queryByText('Logout')).not.toBeInTheDocument();
  });

  it('should display personal data and logout options when authenticated', async () => {
    await renderComponent(true);

    // Click the auth button to open the menu
    await fireEvent.click(screen.getByLabelText('Auth Button'));

    // Check that authenticated options are displayed
    expect(screen.getByText('Personal Data')).toBeInTheDocument();
    expect(screen.getByText('Logout')).toBeInTheDocument();

    // Check that non-authenticated options are not displayed
    expect(screen.queryByText('Login')).not.toBeInTheDocument();
    expect(screen.queryByText('Register')).not.toBeInTheDocument();
  });

  it('should have correct route links for non-authenticated options', async () => {
    await renderComponent(false);

    // Click the auth button to open the menu
    fireEvent.click(screen.getByLabelText('Auth Button'));

    // Check that login button has correct route
    const loginButton = screen.getByText('Login').closest('button');
    expect(loginButton).toHaveAttribute('routerlink', '/auth/login');

    // Check that register button has correct route
    const registerButton = screen.getByText('Register').closest('button');
    expect(registerButton).toHaveAttribute('routerlink', '/auth/register');
  });

  it('should have correct route links for authenticated options', async () => {
    await renderComponent(true);

    // Click the auth button to open the menu
    fireEvent.click(screen.getByLabelText('Auth Button'));

    // Check that personal data button has correct route
    const personalDataButton = screen.getByText('Personal Data').closest('button');
    expect(personalDataButton).toHaveAttribute('routerlink', '/auth/personal-data');

    // Check that logout button has correct route
    const logoutButton = screen.getByText('Logout').closest('button');
    expect(logoutButton).toHaveAttribute('routerlink', '/auth/logout');
  });

  it('should display the person icon in the button', async () => {
    await renderComponent(false);

    const iconElement = screen.getByText('person');
    expect(iconElement).toBeInTheDocument();
  });
});
