import { describe, expect, it } from 'vitest';
import { RoleDefinition } from './role-definition';
import { RoleDefinitionMapper } from './role-definition.mapper';
import { OTHER_ROLE_DEFINITION_DTO, ROLE_DEFINITION_DTO } from './test-role-definition';

describe('RoleDefinitionMapper', () => {
  const mapper = new RoleDefinitionMapper();

  describe('fromDto', () => {
    it('reads the seeded role', () => {
      const role = mapper.fromDto(ROLE_DEFINITION_DTO);

      expect(role).toBeInstanceOf(RoleDefinition);
      expect(role.id).toBe('clerk');
      expect(role.name).toBe('Order Clerk');
      expect(role.entityRoleId).toBe('clerk-role');
      expect(role.version).toBe(1);
    });

    it('leaves an absent base-entity link absent rather than blanking it', () => {
      expect(mapper.fromDto(OTHER_ROLE_DEFINITION_DTO).entityRoleId).toBeUndefined();
    });
  });

  describe('toDto', () => {
    it('round-trips the seeded role', () => {
      expect(mapper.toDto(mapper.fromDto(ROLE_DEFINITION_DTO))).toEqual(ROLE_DEFINITION_DTO);
    });

    // Listed field by field rather than spread, so a control the form may gain later cannot leak into
    // the full-replacement PUT unnoticed.
    it('emits exactly the contract’s fields and nothing else', () => {
      const dto = mapper.toDto(mapper.fromDto(ROLE_DEFINITION_DTO));

      expect(Object.keys(dto).sort()).toEqual(['createdAt', 'description', 'entityRoleId', 'id', 'name', 'updatedAt', 'version']);
    });
  });
});
