import { describe, expect, it } from 'vitest';
import { OrganizationRole, OrganizationUser } from './organization-user';
import { OrganizationRoleMapper, OrganizationUserMapper } from './organization-user.mapper';

describe('OrganizationUser', () => {
  it('is enabled by default, because an invitation creates a usable account', () => {
    expect(new OrganizationUser('kc-1', 'ada').enabled).toBe(true);
  });

  it('shows a full name when it has one and falls back to the username', () => {
    expect(new OrganizationUser('kc-1', 'ada', undefined, 'Ada', 'Lovelace').displayName).toBe('Ada Lovelace');
    expect(new OrganizationUser('kc-1', 'ada', undefined, 'Ada').displayName).toBe('Ada');
    expect(new OrganizationUser('kc-1', 'ada').displayName).toBe('ada');
  });

  it('joins its roles for the read-only table column', () => {
    const withRoles = new OrganizationUser('kc-1', 'ada', undefined, undefined, undefined, true, false, undefined, ['org-admin', 'org-member']);

    expect(withRoles.roleNames).toBe('org-admin, org-member');
    expect(new OrganizationUser('kc-1', 'ada').roleNames).toBe('');
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
