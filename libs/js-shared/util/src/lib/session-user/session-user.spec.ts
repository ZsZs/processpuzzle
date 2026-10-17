import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { SessionUser } from './session-user';

describe('SessionUser', () => {
  let sessionUser: SessionUser;

  beforeEach(() => {
    sessionUser = TestBed.inject(SessionUser);
  });

  it('is empty until somebody signs in', () => {
    expect(sessionUser.userId()).toBe('');
    expect(sessionUser.displayName()).toBe('');
  });

  it('holds the signed-in user, with no name when the identity provider knows none', () => {
    sessionUser.set({ userId: 'user-1', displayName: 'Ada Lovelace' });
    expect(sessionUser.userId()).toBe('user-1');
    expect(sessionUser.displayName()).toBe('Ada Lovelace');

    sessionUser.set({ userId: 'user-2' });
    expect(sessionUser.displayName()).toBe('');
  });

  it('is emptied again on sign-out', () => {
    sessionUser.set({ userId: 'user-1', displayName: 'Ada Lovelace' });

    sessionUser.clear();

    expect(sessionUser.userId()).toBe('');
    expect(sessionUser.displayName()).toBe('');
  });
});
