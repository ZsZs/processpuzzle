import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideFirebaseAuthService } from './provide-firebase-auth-service';
import { FirebaseAuthService } from './firebase-auth.service';
import { BaseConfiguration } from '@processpuzzle/util';
import { AuthenticationConfiguration } from './provide-authentication.service';
import { Auth } from '@angular/fire/auth';

const mockAuth = { name: 'mockAuth' } as Auth;

/*
vi.mock('@angular/fire/auth', () => {
  return {
    default: {},
    Auth: vi.fn(),
    getAuth: vi.fn(() => mockAuth),
    connectAuthEmulator: vi.fn(),
    signInWithEmailAndPassword: vi.fn(),
  };
});
*/

describe.skip('provideFirebaseAuthService', () => {
  let routerMock: Partial<Router>;
  let getAuthMock: ReturnType<typeof vi.fn>;
  let connectAuthEmulatorMock: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    vi.clearAllMocks();
    const firebaseAuthModule = await import('@angular/fire/auth');
    getAuthMock = vi.mocked(firebaseAuthModule.getAuth);
    connectAuthEmulatorMock = vi.mocked(firebaseAuthModule.connectAuthEmulator);

    routerMock = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: Auth, useValue: mockAuth },
        { provide: Router, useValue: routerMock },
      ],
    });
  });

  it('should return a FirebaseAuthService instance', () => {
    const baseConfig = { PIPELINE_STAGE: 'prod' } as BaseConfiguration;
    const authConfig = {} as AuthenticationConfiguration;

    let service: FirebaseAuthService | undefined;
    TestBed.runInInjectionContext(() => {
      service = provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(service).toBeInstanceOf(FirebaseAuthService);
    expect(getAuthMock).toHaveBeenCalled();
  });

  it('should connect to emulator when stage is dev and AUTHENTICATION_SERVICE_ROOT is provided', () => {
    const baseConfig = { PIPELINE_STAGE: 'dev' } as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    TestBed.runInInjectionContext(() => {
      provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(connectAuthEmulatorMock).toHaveBeenCalledWith(mockAuth, 'http://localhost:9099');
  });

  it('should connect to emulator when stage is ci and AUTHENTICATION_SERVICE_ROOT is provided', () => {
    const baseConfig = { PIPELINE_STAGE: 'ci' } as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    TestBed.runInInjectionContext(() => {
      provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(connectAuthEmulatorMock).toHaveBeenCalledWith(mockAuth, 'http://localhost:9099');
  });

  it('should connect to emulator when stage is missing (defaults to ci) and AUTHENTICATION_SERVICE_ROOT is provided', () => {
    const baseConfig = {} as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    TestBed.runInInjectionContext(() => {
      provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(connectAuthEmulatorMock).toHaveBeenCalledWith(mockAuth, 'http://localhost:9099');
  });

  it('should NOT connect to emulator when stage is prod', () => {
    const baseConfig = { PIPELINE_STAGE: 'prod' } as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    TestBed.runInInjectionContext(() => {
      provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(connectAuthEmulatorMock).not.toHaveBeenCalled();
  });

  it('should NOT connect to emulator when AUTHENTICATION_SERVICE_ROOT is missing', () => {
    const baseConfig = { PIPELINE_STAGE: 'dev' } as BaseConfiguration;
    const authConfig = {} as AuthenticationConfiguration;

    TestBed.runInInjectionContext(() => {
      provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(connectAuthEmulatorMock).not.toHaveBeenCalled();
  });
});
