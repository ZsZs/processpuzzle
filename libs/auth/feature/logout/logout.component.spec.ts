import '../../test-setup';
import { setupMockAuthService } from '../../test-setup';
import { fireEvent, screen } from '@testing-library/angular';
import { LogoutComponent } from './logout.component';
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth/domain';
import { NavigateBackService } from '@processpuzzle/widgets';
import { HighContrastModeDetector } from '@angular/cdk/a11y';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogActions, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { mockLanguageConfig, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import authDe from '../assets/i18n/auth/de.json';
import authEn from '../assets/i18n/auth/en.json';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { ComponentFixture } from '@angular/core/testing';
import { of } from 'rxjs';
import { MatButton } from '@angular/material/button';

describe('LogoutComponent', () => {
  const authServiceMock = { logout: vi.fn().mockResolvedValue(undefined) };
  const hcStub = {
    isHighContrastMode: () => of(false),
    // or if your code subscribes to a property:
    // isHighContrastMode$: new BehaviorSubject(false)
  };
  const matDialogRefStub = { close: vi.fn() };
  const navigateBackServiceMock = {
    goBack: vi.fn(),
    getRouteStack: vi.fn().mockReturnValue({ pop: vi.fn() }),
  };
  const testConfig: TranslocoTestConfig = {
    scope: 'auth',
    translations: {
      en: { auth: { authEn } },
      de: { auth: { authDe } },
    },
  };
  let fixture: ComponentFixture<LogoutComponent>;

  beforeEach(async () => {
    vi.clearAllMocks();
    const result = await setUpTranslocoTestBed(LogoutComponent, testConfig, {
      imports: [MatDialogTitle, MatDialogContent, MatDialogActions, MatButton],
      providers: [
        provideNoopAnimations(),
        { provide: AUTHENTICATION_SERVICE, useValue: setupMockAuthService() },
        { provide: HighContrastModeDetector, useValue: hcStub },
        { provide: MatDialogRef, useValue: matDialogRefStub },
        { provide: NavigateBackService, useValue: navigateBackServiceMock },
        { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig },
      ],
    });
    fixture = result.fixture;
  });

  it('should render the logout confirmation dialog', () => {
    expect(screen.getByText('Confirm Logout')).toBeInTheDocument();
    expect(screen.getByText('Are you sure you want to log out?')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /logout/i })).toBeInTheDocument();
  });

  it('should call navigateBackService.goBack when Cancel button is clicked', async () => {
    const cancelButton = screen.getByRole('button', { name: /cancel/i });
    fireEvent.click(cancelButton);

    expect(navigateBackServiceMock.goBack).toHaveBeenCalledTimes(1);
    expect(authServiceMock.logout).not.toHaveBeenCalled();
  });

  it('should call authService.signOut and navigateBackService.goBack when Logout button is clicked', async () => {
    const logoutButton = screen.getByRole('button', { name: /logout/i });
    fireEvent.click(logoutButton);
    await new Promise(process.nextTick);

    expect(authServiceMock.logout).toHaveBeenCalledTimes(1);
    expect(navigateBackServiceMock.goBack).toHaveBeenCalledTimes(1);
  });

  it('should disable the Logout button while logging out', async () => {
    authServiceMock.logout.mockImplementationOnce(() => {
      return new Promise((resolve) => {
        setTimeout(() => resolve(undefined), 100);
      });
    });

    const logoutButton = screen.getByRole('button', { name: /logout/i });

    expect(logoutButton).not.toBeDisabled();

    fireEvent.click(logoutButton);
    fixture.detectChanges();

    expect(logoutButton).toBeDisabled();

    await new Promise((resolve) => setTimeout(resolve, 200));

    expect(logoutButton).not.toBeDisabled();
  });

  it('should handle errors during logout', async () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {
      /* empty */
    });
    const error = new Error('Logout failed');
    authServiceMock.logout.mockRejectedValueOnce(error);
    //    await renderComponent();
    const logoutButton = screen.getByRole('button', { name: /logout/i });
    fireEvent.click(logoutButton);
    await new Promise(process.nextTick);

    expect(consoleErrorSpy).toHaveBeenCalledWith('Error during logout:', error);
    expect(logoutButton).toBeDisabled();
    expect(navigateBackServiceMock.goBack).not.toHaveBeenCalled();

    consoleErrorSpy.mockRestore();
  });
});
