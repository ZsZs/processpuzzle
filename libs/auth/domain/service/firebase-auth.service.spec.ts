import { FirebaseAuthService } from './firebase-auth.service';
import { Auth, signInWithEmailAndPassword } from '@angular/fire/auth';
import { Router } from '@angular/router';
import { User } from '../user/user';
import { TestBed } from '@angular/core/testing';

jest.mock('@angular/fire/auth', () => ({
  signInWithEmailAndPassword: jest.fn(),
}));

describe('FirebaseAuthService', () => {
  let service: FirebaseAuthService;
  let authMock: Partial<Auth>;
  let routerMock: Partial<Router>;

  beforeEach(() => {
    authMock = {
      currentUser: null,
      signOut: jest.fn().mockResolvedValue(undefined),
    };

    routerMock = {
      navigate: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: Auth, useValue: authMock },
        { provide: Router, useValue: routerMock },
      ],
    });

    TestBed.runInInjectionContext(() => {
      service = new FirebaseAuthService(authMock as Auth);
    });
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('authenticate', () => {
    it('should return true if currentUser is not null', async () => {
      (authMock as any).currentUser = { uid: '123' };
      const result = await service.authenticate();
      expect(result).toBe(true);
    });

    it('should return false if currentUser is null', async () => {
      (authMock as any).currentUser = null;
      const result = await service.authenticate();
      expect(result).toBe(false);
    });
  });

  describe('login', () => {
    beforeEach(() => {
      jest.clearAllMocks();
    });

    it('should call signInWithEmailAndPassword and return user when email and password are provided', async () => {
      const mockUserCredential = {
        user: {
          email: 'test@example.com',
          uid: 'uid123',
          displayName: 'Test User',
        },
      };
      (signInWithEmailAndPassword as jest.Mock).mockResolvedValue(mockUserCredential);

      // Update authMock to simulate successful login impact on getCurrentUser
      (authMock as any).currentUser = mockUserCredential.user;

      const result = await service.login('url', 'test@example.com', 'password');

      expect(signInWithEmailAndPassword).toHaveBeenCalledWith(authMock, 'test@example.com', 'password');
      expect(result).toBeInstanceOf(User);
      expect(result?.email).toBe('test@example.com');
      expect(result?.id).toBe('uid123');
      expect(service.getCurrentUser()?.email).toBe('test@example.com');
    });

    it('should return undefined if email or password is missing', async () => {
      const result = await service.login('url', 'test@example.com', undefined);
      expect(result).toBeUndefined();
      expect(signInWithEmailAndPassword).not.toHaveBeenCalled();
    });
  });

  describe('logout', () => {
    it('should call auth.signOut', async () => {
      await service.logout();
      expect(authMock.signOut).toHaveBeenCalled();
    });
  });

  describe('getCurrentUser', () => {
    it('should return User object based on auth.currentUser', () => {
      (authMock as any).currentUser = {
        email: 'test@example.com',
        uid: 'uid123',
        displayName: 'Test User',
      };

      const result = service.getCurrentUser();
      expect(result).toBeInstanceOf(User);
      expect(result?.email).toBe('test@example.com');
      expect(result?.id).toBe('uid123');
    });

    it('should return User with empty/undefined fields if auth.currentUser is null', () => {
      (authMock as any).currentUser = null;
      const result = service.getCurrentUser();
      expect(result).toBeInstanceOf(User);
      expect(result?.email).toBeUndefined();
    });
  });
});
