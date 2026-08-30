import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { RoleDefinition } from './role-definition';

/** The wire shape of `RoleDefinition`, exactly as it travels. */
interface RoleDefinitionDto {
  id?: string;
  name?: string;
  description?: string;
  entityRoleId?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Translates between the `RoleDefinition` DTO of `base-workflow-api.yaml` and the entity the
 * generated screens work with.
 *
 * Every field agrees with the contract, so this is a straight copy — and it is written out field by
 * field rather than spread anyway, so a control the form may gain later cannot leak into the payload
 * unnoticed. `PUT /roles/{roleId}` is a full replacement, so an omitted field is a cleared one.
 */
@Injectable({ providedIn: 'root' })
export class RoleDefinitionMapper implements BaseEntityMapper<RoleDefinition> {
  fromDto(dto: unknown): RoleDefinition {
    const source = dto as RoleDefinitionDto;
    return new RoleDefinition({
      id: source.id,
      name: source.name,
      description: source.description,
      entityRoleId: source.entityRoleId,
      version: source.version,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    });
  }

  toDto(entity: RoleDefinition): RoleDefinitionDto {
    return {
      id: entity.id,
      name: entity.name,
      description: entity.description,
      entityRoleId: entity.entityRoleId,
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}
