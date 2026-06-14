import { beforeEach, describe, expect, it, Mock, vi } from 'vitest';
import { Auth, connectAuthEmulator, getAuth } from '@angular/fire/auth';
import { BaseConfiguration } from '@processpuzzle/util';
import { provideFirebaseAuthService } from './provide-firebase-auth-service';
import { FirebaseAuthService } from './firebase-auth.service';
import { AuthenticationConfiguration } from './provide-authentication.service';

const mockAuth = { name: 'mockAuth' } as unknown as Auth;

vi.mock('@angular/fire/auth', async () => {
  const actual = await vi.importActual<typeof import('@angular/fire/auth')>('@angular/fire/auth');
  return {
    ...actual,
    getAuth: vi.fn(() => mockAuth),
    connectAuthEmulator: vi.fn(),
  };
});

describe('provideFirebaseAuthService', () => {
  const getAuthMock = getAuth as unknown as Mock;
  const connectAuthEmulatorMock = connectAuthEmulator as unknown as Mock;

  beforeEach(() => {
    vi.clearAllMocks();
    getAuthMock.mockReturnValue(mockAuth);
  });

  // region instance creation
  it('should return a FirebaseAuthService instance built from getAuth()', () => {
    const baseConfig = { PIPELINE_STAGE: 'prod' } as BaseConfiguration;
    const authConfig = {} as AuthenticationConfiguration;

    const service = provideFirebaseAuthService(baseConfig, authConfig);

    expect(service).toBeInstanceOf(FirebaseAuthService);
    expect(getAuthMock).toHaveBeenCalledTimes(1);
  });
  // endregion

  // region emulator wiring - positive branches
  it('should connect to the emulator when PIPELINE_STAGE is "dev" and AUTHENTICATION_SERVICE_ROOT is set', () => {
    const baseConfig = { PIPELINE_STAGE: 'dev' } as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    provideFirebaseAuthService(baseConfig, authConfig);

    expect(connectAuthEmulatorMock).toHaveBeenCalledWith(mockAuth, 'http://localhost:9099');
  });

  it('should connect to the emulator when PIPELINE_STAGE is "ci" and AUTHENTICATION_SERVICE_ROOT is set', () => {
    const baseConfig = { PIPELINE_STAGE: 'ci' } as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    provideFirebaseAuthService(baseConfig, authConfig);

    expect(connectAuthEmulatorMock).toHaveBeenCalledWith(mockAuth, 'http://localhost:9099');
  });

  it('should default to "ci" and connect to the emulator when PIPELINE_STAGE is undefined', () => {
    const baseConfig = {} as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    provideFirebaseAuthService(baseConfig, authConfig);

    expect(connectAuthEmulatorMock).toHaveBeenCalledWith(mockAuth, 'http://localhost:9099');
  });
  // endregion

  // region emulator wiring - negative branches
  it('should NOT connect to the emulator when PIPELINE_STAGE is "prod"', () => {
    const baseConfig = { PIPELINE_STAGE: 'prod' } as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    provideFirebaseAuthService(baseConfig, authConfig);

    expect(connectAuthEmulatorMock).not.toHaveBeenCalled();
  });

  it('should NOT connect to the emulator when AUTHENTICATION_SERVICE_ROOT is missing, even in dev', () => {
    const baseConfig = { PIPELINE_STAGE: 'dev' } as BaseConfiguration;
    const authConfig = {} as AuthenticationConfiguration;

    provideFirebaseAuthService(baseConfig, authConfig);

    expect(connectAuthEmulatorMock).not.toHaveBeenCalled();
  });

  it('should NOT connect to the emulator when both PIPELINE_STAGE is prod and AUTHENTICATION_SERVICE_ROOT is missing', () => {
    const baseConfig = { PIPELINE_STAGE: 'prod' } as BaseConfiguration;
    const authConfig = {} as AuthenticationConfiguration;

    provideFirebaseAuthService(baseConfig, authConfig);

    expect(connectAuthEmulatorMock).not.toHaveBeenCalled();
  });
  // endregion
});
