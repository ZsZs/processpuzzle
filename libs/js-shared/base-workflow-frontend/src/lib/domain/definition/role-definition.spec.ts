import { describe, expect, it } from 'vitest';
import { RoleDefinition } from './role-definition';

describe('RoleDefinition', () => {
  it('mints a blank record a New can open a form on', () => {
    const role = new RoleDefinition();

    expect(role.id).toBe('');
    expect(role.name).toBe('');
  });

  // Optional by contract, and left absent rather than blanked: an unset `entityRoleId` means the
  // backend checks no base-entity role, which is not the same as checking for the empty one.
  it('leaves the link into base-entity undefined when nothing set it', () => {
    expect(new RoleDefinition({ id: 'clerk' }).entityRoleId).toBeUndefined();
  });

  it('leaves the server-assigned fields undefined until a backend fills them', () => {
    const role = new RoleDefinition({ id: 'clerk' });

    expect(role.version).toBeUndefined();
    expect(role.createdAt).toBeUndefined();
    expect(role.updatedAt).toBeUndefined();
  });
});
