import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApplicationInitStatus, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AUTHENTICATION_SERVICE, AuthenticationConfiguration, displayNameOf, provideAuthenticationService } from './provide-authentication.service';
import { RUNTIME_CONFIGURATION, SessionUser } from '@processpuzzle/util';
import { User } from '../user/user';
import { KeycloakAuthService } from './keycloak-auth.service';

describe('provideAuthenticationService', () => {
  const mockBaseConfig = { PIPELINE_STAGE: 'prod' };
  const keycloakConfig: AuthenticationConfiguration = {
    AUTHENTICATION_PROVIDER: 'keycloak',
    AUTH_SERVICE_CONFIG: {
      realm: 'test-realm',
      clientId: 'test-client',
      authServerUrl: 'https://test-auth-server',
    },
  };

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function setupTestBed(runtimeConfig: any) { // eslint-disable-line @typescript-eslint/no-explicit-any
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

  // What the feature libraries read: base-entity's comment author, base-workflow's inbox owner.
  it('keeps SessionUser in step with the signed-in user', async () => {
    vi.spyOn(KeycloakAuthService.prototype, 'authenticate').mockResolvedValue(true);
    setupTestBed({ BASE_CONFIGURATION: mockBaseConfig, AUTHENTICATION_CONFIGURATION: keycloakConfig });
    await TestBed.inject(ApplicationInitStatus).donePromise;
    const user = (TestBed.inject(AUTHENTICATION_SERVICE) as unknown as { _user: WritableSignal<User | undefined> })._user;
    const sessionUser = TestBed.inject(SessionUser);

    user.set(new User('ada@example.test', 'user-1', 'Ada', 'Lovelace'));
    TestBed.tick();
    expect(sessionUser.userId()).toBe('user-1');
    expect(sessionUser.displayName()).toBe('Ada Lovelace');

    user.set(undefined);
    TestBed.tick();
    expect(sessionUser.userId()).toBe('');
    expect(sessionUser.displayName()).toBe('');
  });

  it('leaves SessionUser empty when no authentication service can be built', () => {
    setupTestBed({ BASE_CONFIGURATION: mockBaseConfig, AUTHENTICATION_CONFIGURATION: {} });

    TestBed.tick();

    expect(TestBed.inject(SessionUser).userId()).toBe('');
  });
});

describe('displayNameOf', () => {
  it('is the full name, else the email, else empty', () => {
    expect(displayNameOf(new User('ada@example.test', 'u', 'Ada', 'Lovelace'))).toBe('Ada Lovelace');
    expect(displayNameOf(new User('ada@example.test', 'u', ' ', ''))).toBe('ada@example.test');
    expect(displayNameOf(new User(null, 'u'))).toBe('');
    expect(displayNameOf(undefined)).toBe('');
  });
});
