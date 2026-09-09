import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { CurrentUserContext, PROCESS_OWNER_ROLE } from './current-user.context';

describe('CurrentUserContext', () => {
  let context: CurrentUserContext;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [CurrentUserContext] });
    context = TestBed.inject(CurrentUserContext);
  });

  it('starts as an empty session, so the library renders standalone', () => {
    expect(context.userId()).toBe('');
    expect(context.roles()).toEqual([]);
  });

  it('takes the session the host pushes into it', () => {
    context.set({ userId: 'clerk-user', roles: ['clerk'] });

    expect(context.userId()).toBe('clerk-user');
    expect(context.roles()).toEqual(['clerk']);
  });

  it('treats an absent role list as no roles rather than as undefined', () => {
    context.set({ userId: 'clerk-user' });

    expect(context.roles()).toEqual([]);
  });

  describe('mayHoldRole — the permissive question, asked of the Team queue', () => {
    /**
     * The decision this predicate exists for. A host that has not wired roles yet would otherwise get a
     * permanently empty Team queue, indistinguishable from one with nothing in it — hiding the screen
     * rather than restricting it. The backend stays the authority: `assignTask` refuses a claim by a user
     * without the role.
     */
    it('matches everything when the session states no roles, because unknown is not none', () => {
      expect(context.mayHoldRole('clerk')).toBe(true);
      expect(context.mayHoldRole()).toBe(true);
    });

    it('matches any of the names it is given once roles are known', () => {
      context.set({ userId: 'clerk-user', roles: ['clerk'] });

      expect(context.mayHoldRole('manager', 'clerk')).toBe(true);
      expect(context.mayHoldRole('manager')).toBe(false);
    });

    // The three things a host might call a role are offered together, and undefined ones are skipped
    // rather than counted as a match.
    it('ignores undefined names', () => {
      context.set({ userId: 'clerk-user', roles: ['clerk'] });

      expect(context.mayHoldRole(undefined, undefined)).toBe(false);
      expect(context.mayHoldRole(undefined, 'clerk')).toBe(true);
    });

    // A known session with no matching role is a real "no", unlike an unknown one.
    it('matches nothing for a session that states roles and holds none of them', () => {
      context.set({ userId: 'clerk-user', roles: ['clerk'] });

      expect(context.mayHoldRole()).toBe(false);
    });
  });

  describe('hasRole — the strict question, asked before offering an override', () => {
    // The opposite default from mayHoldRole, deliberately: a host that has not wired roles gets no Skip
    // button, which is the safe direction for an action whose point is to bypass a rule.
    it('holds nothing for an unknown session', () => {
      expect(context.hasRole(PROCESS_OWNER_ROLE)).toBe(false);
    });

    it('holds only what the session states', () => {
      context.set({ userId: 'boss', roles: [PROCESS_OWNER_ROLE] });

      expect(context.hasRole(PROCESS_OWNER_ROLE)).toBe(true);
      expect(context.hasRole('clerk')).toBe(false);
    });
  });
});
