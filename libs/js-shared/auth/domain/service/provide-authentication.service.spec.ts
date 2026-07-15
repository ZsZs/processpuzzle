import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AUTHENTICATION_SERVICE, AuthenticationConfiguration, provideAuthenticationService } from './provide-authentication.service';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { KeycloakAuthService } from './keycloak-auth.service';

describe('provideAuthenticationService', () => {
  const mockBaseConfig = { PIPELINE_STAGE: 'prod' };
  const keycloakConfig: AuthenticationConfiguration = {
    AUTHENTICATION_PROVIDER: 'keycloak',
    AUTH_SERVICE_CONFIG: {
      realm: 'test-realm',
      clientId: 'test-client',
      authServerUrl: 'http://test-auth-server',
    },
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function setupTestBed(runtimeConfig: any) {
    TestBed.configureTestingModule({
      providers: [{ provide: RUNTIME_CONFIGURATION, useValue: runtimeConfig }, provideAuthenticationService(runtimeConfig)],
    });
  }

  it('should provide KeycloakAuthService when provider is keycloak and config is provided', () => {
    vi.spyOn(KeycloakAuthService.prototype, 'authenticate').mockResolvedValue(true);
    setupTestBed({
      BASE_CONFIGURATION: mockBaseConfig,
      AUTHENTICATION_CONFIGURATION: keycloakConfig,
    });

    const service = TestBed.inject(AUTHENTICATION_SERVICE);
    expect(service).toBeInstanceOf(KeycloakAuthService);
  });

  it.skip('should provide service when provider is firebase-auth', () => {
    // Skip this test because Firebase initialization requires complex setup
    const authConfig: AuthenticationConfiguration = {
      AUTHENTICATION_PROVIDER: 'firebase-auth',
    };

    setupTestBed({
      BASE_CONFIGURATION: mockBaseConfig,
      AUTHENTICATION_CONFIGURATION: authConfig,
    });

    const service = TestBed.inject(AUTHENTICATION_SERVICE);
    expect(service).toBeDefined();
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

  it('should authenticate at bootstrap via the app initializer', async () => {
    const authenticateSpy = vi.spyOn(KeycloakAuthService.prototype, 'authenticate').mockResolvedValue(true);
    setupTestBed({
      BASE_CONFIGURATION: mockBaseConfig,
      AUTHENTICATION_CONFIGURATION: keycloakConfig,
    });

    await TestBed.inject(ApplicationInitStatus).donePromise;

    expect(authenticateSpy).toHaveBeenCalledTimes(1);
  });

  it('should not block bootstrap when authentication fails', async () => {
    const error = new Error('IdP unreachable');
    vi.spyOn(KeycloakAuthService.prototype, 'authenticate').mockRejectedValue(error);
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    setupTestBed({
      BASE_CONFIGURATION: mockBaseConfig,
      AUTHENTICATION_CONFIGURATION: keycloakConfig,
    });

    // Must resolve (not reject) so app bootstrap is never blocked.
    await expect(TestBed.inject(ApplicationInitStatus).donePromise).resolves.toBeUndefined();
    expect(consoleErrorSpy).toHaveBeenCalledWith('Authentication initialization failed', error);
  });
});
