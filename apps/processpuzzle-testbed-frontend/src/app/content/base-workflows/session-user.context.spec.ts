import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth';
import { describe, expect, it } from 'vitest';
import { SessionUserContext } from './session-user.context';

describe('SessionUserContext', () => {
  it('tracks the authenticated user as the session changes', () => {
    const currentUser = signal<{ id: string } | undefined>(undefined);
    TestBed.configureTestingModule({
      providers: [SessionUserContext, { provide: AUTHENTICATION_SERVICE, useValue: { currentUser } }],
    });

    const context = TestBed.inject(SessionUserContext);
    TestBed.flushEffects();
    expect(context.userId()).toBe('');
    expect(context.roles()).toEqual([]);

    currentUser.set({ id: 'user-1' });
    TestBed.flushEffects();
    expect(context.userId()).toBe('user-1');
    expect(context.roles()).toEqual([]);
  });

  it('keeps an empty session when authentication is not configured', () => {
    TestBed.configureTestingModule({ providers: [SessionUserContext] });

    const context = TestBed.inject(SessionUserContext);
    TestBed.flushEffects();

    expect(context.userId()).toBe('');
    expect(context.mayHoldRole('reviewer')).toBe(true);
    expect(context.hasRole('reviewer')).toBe(false);
  });
});
