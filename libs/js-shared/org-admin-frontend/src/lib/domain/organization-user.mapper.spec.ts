import { describe, expect, it } from 'vitest';
import { OrganizationUser } from './organization-user';
import { OrganizationRoleMapper, OrganizationUserMapper } from './organization-user.mapper';

describe('OrganizationUserMapper', () => {
  const mapper = new OrganizationUserMapper();

  it('reads every field the contract declares', () => {
    const user = mapper.fromDto({
      id: 'kc-1',
      username: 'ada',
      email: 'ada@my-org.example',
      firstName: 'Ada',
      lastName: 'Lovelace',
      enabled: false,
      emailVerified: true,
      createdAt: '2026-08-01T00:00:00Z',
      roles: ['org-member'],
    });

    expect(user.id).toBe('kc-1');
    expect(user.username).toBe('ada');
    expect(user.enabled).toBe(false);
    expect(user.emailVerified).toBe(true);
    expect(user.roles).toEqual(['org-member']);
  });

  // The two load-bearing omissions. `username` is what audit records name, so the platform treats it
  // as immutable; roles have their own endpoint precisely so that a name correction cannot rewrite
  // somebody's permissions.
  it('sends neither the username nor the roles', () => {
    const dto = mapper.toDto(
      new OrganizationUser({
        id: 'kc-1',
        username: 'ada',
        email: 'ada@my-org.example',
        firstName: 'Ada',
        lastName: 'Lovelace',
        enabled: false,
        emailVerified: true,
        createdAt: '2026-08-01T00:00:00Z',
        roles: ['org-admin'],
      }),
    ) as object;

    expect(dto).toEqual({ email: 'ada@my-org.example', firstName: 'Ada', lastName: 'Lovelace', enabled: false });
    expect(Object.keys(dto)).not.toContain('username');
    expect(Object.keys(dto)).not.toContain('roles');
    expect(Object.keys(dto)).not.toContain('id');
  });

  it('tolerates a partial payload', () => {
    const user = mapper.fromDto({ id: 'kc-1', username: 'ada' });

    expect(user.roles).toEqual([]);
    expect(user.enabled).toBe(true);
  });
});

describe('OrganizationRoleMapper', () => {
  it('reads a role and its platform-managed flag', () => {
    const role = new OrganizationRoleMapper().fromDto({ name: 'org-admin', description: 'Administers.', platformManaged: true });

    expect(role.name).toBe('org-admin');
    expect(role.description).toBe('Administers.');
    expect(role.platformManaged).toBe(true);
  });
});
