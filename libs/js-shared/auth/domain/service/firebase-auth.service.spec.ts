import { beforeEach, describe, expect, it, Mock, vi } from 'vitest';
import { Auth, User as FirebaseUser } from '@angular/fire/auth';
import { FirebaseAuthService } from './firebase-auth.service';
import { User } from '../user/user';

const firebaseMocks = vi.hoisted(() => ({
  getAuthMock: vi.fn(),
  connectAuthEmulatorMock: vi.fn(),
  signInWithEmailAndPasswordMock: vi.fn(),
}));

vi.mock('@angular/fire/auth', () => ({
  getAuth: firebaseMocks.getAuthMock,
  connectAuthEmulator: firebaseMocks.connectAuthEmulatorMock,
  signInWithEmailAndPassword: firebaseMocks.signInWithEmailAndPasswordMock,
}));

const { signInWithEmailAndPasswordMock } = firebaseMocks;

describe('FirebaseAuthService', () => {
  type AuthMock = { currentUser: FirebaseUser | null; signOut: Mock };
  let service: FirebaseAuthService;
  let authMock: AuthMock;

  beforeEach(() => {
    vi.clearAllMocks();
    authMock = {
      currentUser: null,
      signOut: vi.fn().mockResolvedValue(undefined),
    };
    service = new FirebaseAuthService(authMock as unknown as Auth);
  });

  // region construction
  it('should be created', () => {
    expect(service).toBeTruthy();
  });
  // endregion

  // region authenticate
  describe('authenticate', () => {
    it('should resolve to true when currentUser is set', async () => {
      authMock.currentUser = { uid: '123' } as FirebaseUser;
      await expect(service.authenticate()).resolves.toBe(true);
    });

    it('should resolve to false when currentUser is null', async () => {
      authMock.currentUser = null;
      await expect(service.authenticate()).resolves.toBe(false);
    });
  });
  // endregion

  // region login
  describe('login', () => {
    it('should call signInWithEmailAndPassword and return a User when credentials are provided', async () => {
      const credential = {
        user: { email: 'test@example.com', uid: 'uid123', displayName: 'Test User' },
      };
      signInWithEmailAndPasswordMock.mockResolvedValueOnce(credential);

      const result = await service.login('url', 'test@example.com', 'password');

      expect(signInWithEmailAndPasswordMock).toHaveBeenCalledWith(authMock, 'test@example.com', 'password');
      expect(result).toBeInstanceOf(User);
      expect(result?.email).toBe('test@example.com');
      expect(result?.id).toBe('uid123');
    });

    it('should expose the signed-in user through getCurrentUser after a successful login', async () => {
      const credential = {
        user: { email: 'test@example.com', uid: 'uid123', displayName: 'Test User' },
      };
      signInWithEmailAndPasswordMock.mockResolvedValueOnce(credential);
      authMock.currentUser = credential.user as FirebaseUser;

      await service.login('url', 'test@example.com', 'password');

      expect(service.getCurrentUser()?.email).toBe('test@example.com');
    });

    it('should return undefined and skip signInWithEmailAndPassword when password is missing', async () => {
      const result = await service.login('url', 'test@example.com', undefined);

      expect(result).toBeUndefined();
      expect(signInWithEmailAndPasswordMock).not.toHaveBeenCalled();
    });

    it('should return undefined and skip signInWithEmailAndPassword when email is missing', async () => {
      const result = await service.login('url', undefined, 'password');

      expect(result).toBeUndefined();
      expect(signInWithEmailAndPasswordMock).not.toHaveBeenCalled();
    });

    it('should return undefined when neither email nor password is provided', async () => {
      const result = await service.login();

      expect(result).toBeUndefined();
      expect(signInWithEmailAndPasswordMock).not.toHaveBeenCalled();
    });

    it('should propagate errors from signInWithEmailAndPassword', async () => {
      const error = new Error('invalid credentials');
      signInWithEmailAndPasswordMock.mockRejectedValueOnce(error);

      await expect(service.login('url', 'test@example.com', 'wrong')).rejects.toBe(error);
    });
  });
  // endregion

  // region logout
  describe('logout', () => {
    it('should delegate to auth.signOut', async () => {
      await service.logout();
      expect(authMock.signOut).toHaveBeenCalledTimes(1);
    });

    it('should propagate errors from auth.signOut', async () => {
      const error = new Error('signOut failed');
      authMock.signOut.mockRejectedValueOnce(error);

      await expect(service.logout()).rejects.toBe(error);
    });
  });
  // endregion

  // region getCurrentUser
  describe('getCurrentUser', () => {
    it('should build a User from auth.currentUser when one is signed in', () => {
      authMock.currentUser = {
        email: 'test@example.com',
        uid: 'uid123',
        displayName: 'Test User',
      } as FirebaseUser;

      const result = service.getCurrentUser();

      expect(result).toBeInstanceOf(User);
      expect(result?.email).toBe('test@example.com');
      expect(result?.id).toBe('uid123');
    });

    it('should return a User with undefined email when no one is signed in', () => {
      authMock.currentUser = null;

      const result = service.getCurrentUser();

      expect(result).toBeInstanceOf(User);
      expect(result?.email).toBeUndefined();
    });
  });
  // endregion
});
