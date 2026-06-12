import { beforeEach, describe, expect, it } from 'vitest';
import { User } from './user';

describe('User', () => {
  beforeEach(() => {
    // Test setup
  });

  describe('constructor', () => {
    it('should create a user with required email only', () => {
      const user = new User('test@example.com');

      expect(user.email).toBe('test@example.com');
      expect(user.id).toBeTruthy(); // UUID is generated
      expect(user.firstName).toBe('');
      expect(user.lastName).toBe('');
      expect(user.photoUrl).toBe('');
    });

    it('should create a user with all properties', () => {
      const user = new User('test@example.com', 'custom-id', 'John', 'Doe', 'https://example.com/photo.jpg');

      expect(user.email).toBe('test@example.com');
      expect(user.id).toBe('custom-id');
      expect(user.firstName).toBe('John');
      expect(user.lastName).toBe('Doe');
      expect(user.photoUrl).toBe('https://example.com/photo.jpg');
    });

    it('should use provided id instead of generating one', () => {
      const user = new User('test@example.com', 'custom-id');

      expect(user.id).toBe('custom-id');
    });

    it('should generate id if not provided', () => {
      const user = new User('test@example.com');

      expect(user.id).toBeTruthy();
      expect(user.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    });
  });

  describe('getters and setters', () => {
    let user: User;

    beforeEach(() => {
      user = new User('test@example.com');
    });

    it('should get and set email', () => {
      expect(user.email).toBe('test@example.com');

      user.email = 'new@example.com';
      expect(user.email).toBe('new@example.com');
    });

    it('should get and set firstName', () => {
      expect(user.firstName).toBe('');

      user.firstName = 'John';
      expect(user.firstName).toBe('John');
    });

    it('should get and set lastName', () => {
      expect(user.lastName).toBe('');

      user.lastName = 'Doe';
      expect(user.lastName).toBe('Doe');
    });

    it('should get and set photoUrl', () => {
      expect(user.photoUrl).toBe('');

      user.photoUrl = 'https://example.com/photo.jpg';
      expect(user.photoUrl).toBe('https://example.com/photo.jpg');
    });
  });
});
