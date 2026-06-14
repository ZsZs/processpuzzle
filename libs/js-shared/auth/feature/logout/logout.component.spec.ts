import { beforeEach, describe, expect, it, vi } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { fireEvent, screen } from '@testing-library/angular';
import { ComponentFixture } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogActions, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { NavigateBackService } from '@processpuzzle/widgets';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';
import { mockLanguageConfig, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';

import { LogoutComponent } from './logout.component';
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

describe('LogoutComponent', () => {
  const routeStack = { pop: vi.fn<() => string | undefined>() };
  const navigateBack = {
    goBack: vi.fn(),
    getRouteStack: vi.fn(() => routeStack),
  };
  const matDialogRefStub = { close: vi.fn() };
  let fixture: ComponentFixture<LogoutComponent>;

  const renderComponent = async () => {
    const result = await setUpTranslocoTestBed(LogoutComponent, testConfig, {
      imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatButton],
      providers: [
        provideNoopAnimations(),
        { provide: AUTHENTICATION_SERVICE, useValue: setupMockAuthService() },
        { provide: MatDialogRef, useValue: matDialogRefStub },
        { provide: NavigateBackService, useValue: navigateBack },
        { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig },
      ],
    });
    fixture = result.fixture;
    return result;
  };

  beforeEach(() => {
    vi.clearAllMocks();
    routeStack.pop.mockReset();
  });

  // region Rendering
  it('should render the logout confirmation dialog', async () => {
    await renderComponent();
    expect(screen.getByText('Confirm Logout')).toBeInTheDocument();
    expect(screen.getByText(/Are you sure you want to log out\?/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /logout/i })).toBeInTheDocument();
  });

  it('should not show the logout button as disabled initially', async () => {
    await renderComponent();
    const logoutButton = screen.getByRole('button', { name: /logout/i });
    expect(logoutButton).not.toBeDisabled();
  });
  // endregion

  // region onCancel
  it('should call navigateBackService.goBack when Cancel button is clicked', async () => {
    await renderComponent();
    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));

    expect(navigateBack.goBack).toHaveBeenCalledTimes(1);
    expect(mockAuthService.logout).not.toHaveBeenCalled();
  });
  // endregion

  // region onLogout - success
  it('should pop the current route and pass the previous route to authService.logout', async () => {
    await renderComponent();
    routeStack.pop.mockReturnValueOnce('/current').mockReturnValueOnce('/previous');

    fireEvent.click(screen.getByRole('button', { name: /logout/i }));
    await new Promise(process.nextTick);

    expect(routeStack.pop).toHaveBeenCalledTimes(2);
    expect(mockAuthService.logout).toHaveBeenCalledWith('/previous');
    expect(navigateBack.goBack).toHaveBeenCalledTimes(1);
  });

  it('should call authService.logout with undefined when the route stack is empty', async () => {
    await renderComponent();
    routeStack.pop.mockReturnValue(undefined);

    fireEvent.click(screen.getByRole('button', { name: /logout/i }));
    await new Promise(process.nextTick);

    expect(mockAuthService.logout).toHaveBeenCalledWith(undefined);
    expect(navigateBack.goBack).toHaveBeenCalledTimes(1);
  });

  it('should disable the Logout button while logout is pending and re-enable it after completion', async () => {
    await renderComponent();
    let resolveLogout: (() => void) | undefined;
    mockAuthService.logout = vi.fn().mockImplementation(
      () => new Promise<void>((resolve) => (resolveLogout = resolve)),
    );
    const logoutButton = screen.getByRole('button', { name: /logout/i });
    expect(logoutButton).not.toBeDisabled();

    fireEvent.click(logoutButton);
    fixture.detectChanges();
    expect(logoutButton).toBeDisabled();

    resolveLogout?.();
    await new Promise(process.nextTick);
    fixture.detectChanges();
    expect(logoutButton).not.toBeDisabled();
  });
  // endregion

  // region onLogout - error
  it('should log an error and re-enable the Logout button when logout rejects', async () => {
    await renderComponent();
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {
      /* swallow */
    });
    const error = new Error('Logout failed');
    mockAuthService.logout = vi.fn().mockRejectedValueOnce(error);

    fireEvent.click(screen.getByRole('button', { name: /logout/i }));
    await new Promise(process.nextTick);
    fixture.detectChanges();

    expect(consoleErrorSpy).toHaveBeenCalledWith('Error during logout:', error);
    expect(navigateBack.goBack).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /logout/i })).not.toBeDisabled();

    consoleErrorSpy.mockRestore();
  });
  // endregion
});
