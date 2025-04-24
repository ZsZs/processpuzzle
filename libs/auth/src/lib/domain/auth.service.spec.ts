import { TestBed } from '@angular/core/testing';
import { Auth, User, authState } from '@angular/fire/auth';
import { AuthService } from './auth.service';
import { BehaviorSubject } from 'rxjs';

// Mock the authState function
jest.mock('@angular/fire/auth', () => {
  const authStateMock = jest.fn();
  return {
    Auth: jest.fn(),
    authState: authStateMock,
  };
});

describe('AuthService', () => {
  let service: AuthService;
  let mockAuth: Partial<Auth>;
  let authStateSubject: BehaviorSubject<User | null>;
  let mockCurrentUser: User | null = null;

  beforeEach(() => {
    // Create a subject to control the authState observable
    authStateSubject = new BehaviorSubject<User | null>(null);

    // Setup the mock Auth object with a getter for currentUser
    mockAuth = {
      signOut: jest.fn().mockResolvedValue(undefined),
      get currentUser() { return mockCurrentUser; }
    };

    // Configure authState mock to return our subject as an observable
    (authState as jest.Mock).mockReturnValue(authStateSubject);

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        { provide: Auth, useValue: mockAuth }
      ]
    });

    service = TestBed.inject(AuthService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('user signal', () => {
    it('should initially be null', () => {
      expect(service.user()).toBeNull();
    });

    it('should update when auth state changes', () => {
      const mockUser = { uid: '123', email: 'test@example.com' } as User;
      authStateSubject.next(mockUser);
      expect(service.user()).toEqual(mockUser);
    });
  });

  describe('isAuthenticated signal', () => {
    it('should be false when user is null', () => {
      authStateSubject.next(null);
      expect(service.isAuthenticated()).toBe(false);
    });

    it('should be true when user is not null', () => {
      const mockUser = { uid: '123' } as User;
      authStateSubject.next(mockUser);
      expect(service.isAuthenticated()).toBe(true);
    });
  });

  describe('signOut', () => {
    it('should call auth.signOut', async () => {
      await service.signOut();
      expect(mockAuth.signOut).toHaveBeenCalled();
    });

    it('should return the promise from auth.signOut', async () => {
      const result = service.signOut();
      expect(result).toEqual(expect.any(Promise));
      await result;
    });
  });

  describe('getCurrentUser', () => {
    it('should return null when not authenticated', () => {
      mockCurrentUser = null;
      expect(service.getCurrentUser()).toBeNull();
    });

    it('should return the current user when authenticated', () => {
      mockCurrentUser = { uid: '123', email: 'test@example.com' } as User;
      expect(service.getCurrentUser()).toEqual(mockCurrentUser);
    });
  });
});
