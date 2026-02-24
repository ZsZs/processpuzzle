import { TestBed } from '@angular/core/testing';
import { AUTHENTICATION_SERVICE, AuthenticationConfiguration, provideAuthenticationService } from './provide-authentication.service';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { KeycloakAuthService } from './keycloak-auth.service';
import { provideFirebaseAuthService } from './provide-firebase-auth-service';
import { Mocked } from 'vitest';

vi.mock('keycloak-js', () => ({
  __esModule: true,
  default: vi.fn(),
}));

vi.mock('./keycloak-auth.service');
vi.mock('./provide-firebase-auth-service');

describe('provideAuthenticationService', () => {
  const mockBaseConfig = { PIPELINE_STAGE: 'prod' };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  function setupTestBed(runtimeConfig: any) {
    TestBed.configureTestingModule({
      providers: [{ provide: RUNTIME_CONFIGURATION, useValue: runtimeConfig }, provideAuthenticationService()],
    });
  }

  it('should provide KeycloakAuthService when provider is keycloak and config is provided', () => {
    const authConfig: AuthenticationConfiguration = {
      AUTHENTICATION_PROVIDER: 'keycloak',
      AUTH_SERVICE_CONFIG: {
        realm: 'test-realm',
        clientId: 'test-client',
        authServerUrl: 'http://test-auth-server',
      },
    };

    setupTestBed({
      BASE_CONFIGURATION: mockBaseConfig,
      AUTHENTICATION_CONFIGURATION: authConfig,
    });

    const service = TestBed.inject(AUTHENTICATION_SERVICE);
    expect(KeycloakAuthService).toHaveBeenCalledWith(authConfig.AUTH_SERVICE_CONFIG);
    expect(service).toBeInstanceOf(KeycloakAuthService);
  });

  it('should provide service from provideFirebaseAuthService when provider is firebase-auth', () => {
    const authConfig: AuthenticationConfiguration = {
      AUTHENTICATION_PROVIDER: 'firebase-auth',
    };
    const mockFirebaseService = { name: 'firebase' };
    (provideFirebaseAuthService as Mocked<any>).mockReturnValue(mockFirebaseService);

    setupTestBed({
      BASE_CONFIGURATION: mockBaseConfig,
      AUTHENTICATION_CONFIGURATION: authConfig,
    });

    const service = TestBed.inject(AUTHENTICATION_SERVICE);
    expect(provideFirebaseAuthService).toHaveBeenCalledWith(mockBaseConfig, authConfig);
    expect(service).toBe(mockFirebaseService);
  });

  it('should throw error when provider is missing', () => {
    setupTestBed({
      BASE_CONFIGURATION: mockBaseConfig,
      AUTHENTICATION_CONFIGURATION: {},
    });

    expect(() => TestBed.inject(AUTHENTICATION_SERVICE)).toThrow('No AUTHENTICATION_PROVIDER or AUTH_SERVICE_CONFIG configured. Verify your RUNTIME_CONFIGURATION.');
  });

  it('should throw error when provider is keycloak but config is missing', () => {
    setupTestBed({
      BASE_CONFIGURATION: mockBaseConfig,
      AUTHENTICATION_CONFIGURATION: {
        AUTHENTICATION_PROVIDER: 'keycloak',
      },
    });

    expect(() => TestBed.inject(AUTHENTICATION_SERVICE)).toThrow('No AUTHENTICATION_PROVIDER or AUTH_SERVICE_CONFIG configured. Verify your RUNTIME_CONFIGURATION.');
  });

  it('should throw error for unimplemented provider oauth2', () => {
    setupTestBed({
      BASE_CONFIGURATION: mockBaseConfig,
      AUTHENTICATION_CONFIGURATION: {
        AUTHENTICATION_PROVIDER: 'oauth2',
      },
    });

    expect(() => TestBed.inject(AUTHENTICATION_SERVICE)).toThrow('No AUTHENTICATION_PROVIDER or AUTH_SERVICE_CONFIG configured. Verify your RUNTIME_CONFIGURATION.');
  });
});
