import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideFirebaseAuthService } from './provide-firebase-auth-service';
import { FirebaseAuthService } from './firebase-auth.service';
import { BaseConfiguration } from '@processpuzzle/util';
import { AuthenticationConfiguration } from './provide-authentication.service';
import * as firebaseAuth from '@angular/fire/auth';
import { vi } from 'vitest';

describe('provideFirebaseAuthService', () => {
  let routerMock: Partial<Router>;
  const mockAuth = { name: 'mockAuth' };

  beforeEach(() => {
    vi.clearAllMocks();

    routerMock = {
      navigate: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: Router, useValue: routerMock }],
    });

    (firebaseAuth.getAuth as any).mockReturnValue(mockAuth);
  });

  it('should return a FirebaseAuthService instance', () => {
    const baseConfig = { PIPELINE_STAGE: 'prod' } as BaseConfiguration;
    const authConfig = {} as AuthenticationConfiguration;

    let service: FirebaseAuthService | undefined;
    TestBed.runInInjectionContext(() => {
      service = provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(service).toBeInstanceOf(FirebaseAuthService);
    expect(firebaseAuth.getAuth).toHaveBeenCalled();
  });

  it('should connect to emulator when stage is dev and AUTHENTICATION_SERVICE_ROOT is provided', () => {
    const baseConfig = { PIPELINE_STAGE: 'dev' } as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    TestBed.runInInjectionContext(() => {
      provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(firebaseAuth.connectAuthEmulator).toHaveBeenCalledWith(mockAuth, 'http://localhost:9099');
  });

  it('should connect to emulator when stage is ci and AUTHENTICATION_SERVICE_ROOT is provided', () => {
    const baseConfig = { PIPELINE_STAGE: 'ci' } as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    TestBed.runInInjectionContext(() => {
      provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(firebaseAuth.connectAuthEmulator).toHaveBeenCalledWith(mockAuth, 'http://localhost:9099');
  });

  it('should connect to emulator when stage is missing (defaults to ci) and AUTHENTICATION_SERVICE_ROOT is provided', () => {
    const baseConfig = {} as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    TestBed.runInInjectionContext(() => {
      provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(firebaseAuth.connectAuthEmulator).toHaveBeenCalledWith(mockAuth, 'http://localhost:9099');
  });

  it('should NOT connect to emulator when stage is prod', () => {
    const baseConfig = { PIPELINE_STAGE: 'prod' } as BaseConfiguration;
    const authConfig = { AUTHENTICATION_SERVICE_ROOT: 'http://localhost:9099' } as AuthenticationConfiguration;

    TestBed.runInInjectionContext(() => {
      provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(firebaseAuth.connectAuthEmulator).not.toHaveBeenCalled();
  });

  it('should NOT connect to emulator when AUTHENTICATION_SERVICE_ROOT is missing', () => {
    const baseConfig = { PIPELINE_STAGE: 'dev' } as BaseConfiguration;
    const authConfig = {} as AuthenticationConfiguration;

    TestBed.runInInjectionContext(() => {
      provideFirebaseAuthService(baseConfig, authConfig);
    });

    expect(firebaseAuth.connectAuthEmulator).not.toHaveBeenCalled();
  });
});
