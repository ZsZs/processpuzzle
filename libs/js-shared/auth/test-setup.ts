import { vi } from 'vitest';
import '@testing-library/jest-dom/vitest';

export const mockAuthService = {
  _user: { set: vi.fn() },
  user: vi.fn().mockReturnValue(undefined),
  isAuthenticated: vi.fn().mockReturnValue(false),
  authenticate: vi.fn().mockResolvedValue(false),
  login: vi.fn().mockResolvedValue(undefined),
  logout: vi.fn().mockResolvedValue(undefined),
  getCurrentUser: vi.fn().mockReturnValue(undefined),
  throwError: vi.fn(),
};

export function setupMockAuthService(overrides?: { isAuthenticated?: boolean; authenticate?: boolean; login?: any; logout?: void; getCurrentUser?: any }) {
  const defaults = {
    isAuthenticated: false,
    authenticate: false,
    login: undefined,
    logout: undefined,
    getCurrentUser: undefined,
  };

  const values = { ...defaults, ...overrides };

  mockAuthService.isAuthenticated = vi.fn().mockReturnValue(values.isAuthenticated);
  mockAuthService.authenticate = vi.fn().mockResolvedValue(values.authenticate);
  mockAuthService.login = vi.fn().mockResolvedValue(values.login);
  mockAuthService.logout = vi.fn().mockResolvedValue(values.logout);
  mockAuthService.getCurrentUser = vi.fn().mockReturnValue(values.getCurrentUser);

  return mockAuthService;
}
