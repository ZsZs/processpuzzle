import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth';
import { describe, expect, it } from 'vitest';
import { SessionEntityUserContext } from './session-entity-user.context';

type SessionUser = { id: string; firstName?: string; lastName?: string; email?: string | null };

describe('SessionEntityUserContext', () => {
  it('names the signed-in user by first and last name, falling back to the email', () => {
    const currentUser = signal<SessionUser | undefined>(undefined);
    TestBed.configureTestingModule({
      providers: [SessionEntityUserContext, { provide: AUTHENTICATION_SERVICE, useValue: { currentUser } }],
    });

    const context = TestBed.inject(SessionEntityUserContext);
    TestBed.flushEffects();
    expect(context.userId()).toBe('');
    expect(context.displayName()).toBe('');

    currentUser.set({ id: 'user-1', firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' });
    TestBed.flushEffects();
    expect(context.userId()).toBe('user-1');
    expect(context.displayName()).toBe('Ada Lovelace');

    currentUser.set({ id: 'user-2', firstName: ' ', lastName: '', email: 'grace@example.com' });
    TestBed.flushEffects();
    expect(context.displayName()).toBe('grace@example.com');
  });

  it('keeps an empty session when authentication is not configured', () => {
    TestBed.configureTestingModule({ providers: [SessionEntityUserContext] });

    const context = TestBed.inject(SessionEntityUserContext);
    TestBed.flushEffects();

    expect(context.userId()).toBe('');
    expect(context.displayName()).toBe('');
  });
});
