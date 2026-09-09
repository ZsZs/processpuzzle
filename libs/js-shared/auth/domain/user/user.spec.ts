import { describe, expect, it } from 'vitest';
import { User } from './user';

describe('User', () => {
  it('has no roles by default', () => {
    expect(new User('ada@example.com').roles).toEqual([]);
  });

  it('carries the realm roles it was constructed with', () => {
    const user = new User('ada@example.com', 'kc-1', 'Ada', 'Lovelace', null, ['org-admin', 'org-member']);

    expect(user.roles).toEqual(['org-admin', 'org-member']);
    expect(user.hasRole('org-admin')).toBe(true);
    expect(user.hasRole('platform-admin')).toBe(false);
  });

  // A caller that mutated this list would be editing what the identity provider said, and an
  // authorization check reading it afterwards would be checking the caller's own answer.
  it('copies the roles on the way in and returns them frozen', () => {
    const source = ['org-member'];
    const user = new User('ada@example.com', 'kc-1', null, null, null, source);

    source.push('org-admin');

    expect(user.roles).toEqual(['org-member']);
    expect(() => (user.roles as string[]).push('sneaky')).toThrow();
  });

  it('treats an empty required-role list as "any authenticated user"', () => {
    const user = new User('ada@example.com', 'kc-1', null, null, null, []);

    expect(user.hasAnyRole([])).toBe(true);
    expect(user.hasAnyRole(['org-admin'])).toBe(false);
  });

  it('matches when any one of the required roles is held', () => {
    const user = new User('ada@example.com', 'kc-1', null, null, null, ['claims-auditor']);

    expect(user.hasAnyRole(['claims-approver', 'claims-auditor'])).toBe(true);
  });
});
