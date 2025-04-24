import { fireEvent, render, screen } from '@testing-library/angular';
import { LogoutComponent } from './logout.component';
import { AuthService } from '../domain/auth.service';
import { NavigateBackService } from '@processpuzzle/widgets';
import { MatDialogActions, MatDialogContent, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';

describe('LogoutComponent', () => {
  // Create mocks for the injected services
  const authServiceMock = {
    signOut: jest.fn().mockResolvedValue(undefined),
  };

  const navigateBackServiceMock = {
    goBack: jest.fn(),
  };

  const renderComponent = async () => {
    return render(LogoutComponent, {
      imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatButton],
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: NavigateBackService, useValue: navigateBackServiceMock },
      ],
    });
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render the logout confirmation dialog', async () => {
    await renderComponent();

    expect(screen.getByText('Confirm Logout')).toBeInTheDocument();
    expect(screen.getByText('Are you sure you want to log out?')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /logout/i })).toBeInTheDocument();
  });

  it('should call navigateBackService.goBack when Cancel button is clicked', async () => {
    await renderComponent();

    const cancelButton = screen.getByRole('button', { name: /cancel/i });
    fireEvent.click(cancelButton);

    expect(navigateBackServiceMock.goBack).toHaveBeenCalledTimes(1);
    expect(authServiceMock.signOut).not.toHaveBeenCalled();
  });

  it('should call authService.signOut and navigateBackService.goBack when Logout button is clicked', async () => {
    await renderComponent();

    const logoutButton = screen.getByRole('button', { name: /logout/i });
    fireEvent.click(logoutButton);
    await new Promise(process.nextTick);

    expect(authServiceMock.signOut).toHaveBeenCalledTimes(1);
    expect(navigateBackServiceMock.goBack).toHaveBeenCalledTimes(1);
  });

  it('should disable the Logout button while logging out', async () => {
    authServiceMock.signOut.mockImplementationOnce(() => {
      return new Promise((resolve) => {
        setTimeout(() => resolve(undefined), 100);
      });
    });

    const { fixture } = await renderComponent();
    const logoutButton = screen.getByRole('button', { name: /logout/i });

    expect(logoutButton).not.toBeDisabled();

    fireEvent.click(logoutButton);
    fixture.detectChanges();

    expect(logoutButton).toBeDisabled();

    await new Promise((resolve) => setTimeout(resolve, 200));

    expect(logoutButton).not.toBeDisabled();
  });

  it('should handle errors during logout', async () => {
    const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation();
    const error = new Error('Logout failed');
    authServiceMock.signOut.mockRejectedValueOnce(error);
    await renderComponent();
    const logoutButton = screen.getByRole('button', { name: /logout/i });
    fireEvent.click(logoutButton);
    await new Promise(process.nextTick);

    expect(consoleErrorSpy).toHaveBeenCalledWith('Error during logout:', error);
    expect(logoutButton).toBeDisabled();
    expect(navigateBackServiceMock.goBack).not.toHaveBeenCalled();

    consoleErrorSpy.mockRestore();
  });
});
