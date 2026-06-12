import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { KeycloakAuthService } from './keycloak-auth.service';
import { KeycloakAuthConfig } from './keycloak-auth.config';

type MockedKeycloak = {
  authenticated: boolean;
  profile?: { username?: string };
  realmAccess?: { roles: string[] };
  init: ReturnType<typeof vi.fn>;
  login: ReturnType<typeof vi.fn>;
  logout: ReturnType<typeof vi.fn>;
  loadUserProfile: ReturnType<typeof vi.fn>;
};

// Save original location
const originalLocation = globalThis.location;

describe('KeycloakAuthService', () => {
  let service: KeycloakAuthService;
  let mockKeycloakInstance: MockedKeycloak;
  const config: KeycloakAuthConfig = {
    realm: 'test-realm',
    clientId: 'test-client',
    authServerUrl: 'http://test-auth-server',
  };

  beforeEach(() => {
    // Use happy-dom-compatible location mocking
    (globalThis as any).location = new URL('http://localhost:4200');

    // Create a mock Keycloak instance
    mockKeycloakInstance = {
      init: vi.fn().mockResolvedValue(true),
      login: vi.fn().mockResolvedValue(undefined),
      logout: vi.fn().mockResolvedValue(undefined),
      loadUserProfile: vi.fn().mockResolvedValue({
        id: '123',
        firstName: 'John',
        lastName: 'Doe',
        email: 'john.doe@example.com',
      }),
      authenticated: false,
      profile: { username: 'johndoe' },
      realmAccess: { roles: ['user', 'admin'] },
    } as MockedKeycloak;

    service = new KeycloakAuthService(config);
    // Replace the keycloak instance with our mock
    (service as any).keycloak = mockKeycloakInstance;
  });

  afterEach(() => {
    vi.clearAllMocks();
    (globalThis as any).location = originalLocation;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('authenticate', () => {
    it('should initialize keycloak and return true if authenticated', async () => {
      mockKeycloakInstance.authenticated = true;
      const result = await service.authenticate();
      expect(mockKeycloakInstance.init).toHaveBeenCalled();
      expect(result).toBe(true);
      expect(service.getCurrentUser()?.id).toBe('123');
    });

    it('should return false if not authenticated after initialization', async () => {
      mockKeycloakInstance.authenticated = false;
      const result = await service.authenticate();
      expect(result).toBe(false);
    });
  });

  describe('login', () => {
    it('should call keycloak.login if not already authenticated', async () => {
      mockKeycloakInstance.authenticated = false;
      await service.login('dashboard');
      expect(mockKeycloakInstance.login).toHaveBeenCalledWith({
        redirectUri: (globalThis.location as any).origin + '/dashboard',
      });
    });

    it('should not call keycloak.login if already authenticated', async () => {
      // Manual setup to simulate authenticated state in signals
      mockKeycloakInstance.authenticated = true;
      await service.authenticate(); // This will set the user signal

      vi.clearAllMocks();

      // The login method does: if (this.isAuthenticated()) return new Promise(this.user);
      // We need to bypass the promise which uses a signal as an executor if that's what's happening
      // Actually, new Promise(this.user) where this.user is a Signal is very strange.
      // If this.user() is an object, it's basically new Promise({email: ...}) which might not behave as expected.

      const result = await service.login('dashboard');

      expect(mockKeycloakInstance.login).not.toHaveBeenCalled();
      expect(result).toBeDefined();
      expect(result.id).toBe('123');
    });
  });

  describe('logout', () => {
    it('should call keycloak.logout and clear user signal', async () => {
      await service.logout('home');
      expect(mockKeycloakInstance.logout).toHaveBeenCalledWith({
        redirectUri: (globalThis.location as any).origin + '/home',
      });
      expect(service.getCurrentUser()).toBeUndefined();
    });
  });

  describe('getCurrentUser', () => {
    it('should return undefined initially', () => {
      expect(service.getCurrentUser()).toBeUndefined();
    });

    it('should return user after successful authentication', async () => {
      mockKeycloakInstance.authenticated = true;
      await service.authenticate();
      const user = service.getCurrentUser();
      expect(user).toBeDefined();
      expect(user?.email).toBe('john.doe@example.com');
      expect(user?.id).toBe('123');
    });
  });

  describe('getUsername', () => {
    it('should return username from keycloak profile', () => {
      expect(service.getUsername()).toBe('johndoe');
    });

    it('should return empty string if profile is missing', () => {
      mockKeycloakInstance.profile = undefined;
      expect(service.getUsername()).toBe('');
    });
  });

  describe('getUserRoles', () => {
    it('should return roles from realmAccess', () => {
      expect(service.getUserRoles()).toEqual(['user', 'admin']);
    });

    it('should return empty array if realmAccess is missing', () => {
      mockKeycloakInstance.realmAccess = undefined;
      expect(service.getUserRoles()).toEqual([]);
    });
  });

  describe('initKeycloak (via ensureInitialized)', () => {
    it('should map profile fields to User model correctly', async () => {
      mockKeycloakInstance.authenticated = true;
      mockKeycloakInstance.loadUserProfile.mockResolvedValue({
        id: 'user-id',
        firstName: 'Jane',
        lastName: 'Smith',
        email: 'jane.smith@example.com',
      });

      await service.authenticate();
      const user = service.getCurrentUser();

      expect(user?.id).toBe('user-id');
      expect(user?.email).toBe('jane.smith@example.com');
      expect(user?.firstName).toBe('Jane');
      expect(user?.lastName).toBe('Smith');
    });

    it('should set user to undefined if not authenticated', async () => {
      mockKeycloakInstance.authenticated = false;
      await service.authenticate();
      expect(service.getCurrentUser()).toBeUndefined();
    });
  });
});
