import { describe, expect, it } from 'vitest';
import { OrganizationRole, OrganizationUser } from './organization-user';

describe('OrganizationUser', () => {
  it('is enabled by default, because an invitation creates a usable account', () => {
    expect(new OrganizationUser({ id: 'kc-1', username: 'ada' }).enabled).toBe(true);
  });

  it('shows a full name when it has one and falls back to the username', () => {
    expect(new OrganizationUser({ id: 'kc-1', username: 'ada', firstName: 'Ada', lastName: 'Lovelace' }).displayName).toBe('Ada Lovelace');
    expect(new OrganizationUser({ id: 'kc-1', username: 'ada', firstName: 'Ada' }).displayName).toBe('Ada');
    expect(new OrganizationUser({ id: 'kc-1', username: 'ada' }).displayName).toBe('ada');
  });

  it('joins its roles for the read-only table column', () => {
    const withRoles = new OrganizationUser({ id: 'kc-1', username: 'ada', roles: ['org-admin', 'org-member'] });

    expect(withRoles.roleNames).toBe('org-admin, org-member');
    expect(new OrganizationUser({ id: 'kc-1', username: 'ada' }).roleNames).toBe('');
  });
});

describe('OrganizationRole', () => {
  it('uses its name as its BaseEntity id, because that is what the API keys it by', () => {
    const role = new OrganizationRole('org-admin', 'Administers.', true);

    expect(role.id).toBe('org-admin');
    expect(role.platformManaged).toBe(true);
  });

  it('treats a tenant own role as not platform-managed', () => {
    expect(new OrganizationRole('claims-auditor').platformManaged).toBe(false);
  });
});
