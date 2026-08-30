import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { EntityReference, toReferenceIds } from '../reference-ids';
import { RoleDefinition } from './role-definition';

/**
 * The wire shape of `RoleDefinition`, exactly as it travels. `responsibleFor` is `string[]` by
 * contract; it is typed wider because the `RELATED_ENTITIES` control writes whole entities into its
 * form control on selection — see {@link toReferenceIds}.
 */
interface RoleDefinitionDto {
  id?: string;
  name?: string;
  description?: string;
  responsibleFor?: EntityReference[];
  entityRoleId?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Translates between the `RoleDefinition` DTO of `base-workflow-api.yaml` and the entity the
 * generated screens work with.
 *
 * `responsibleFor` is flattened in both directions, as everywhere a `RELATED_ENTITIES` control faces
 * an id list: the control writes whole entities into its form control when the user picks one, while
 * the contract wants `string[]`. {@link toReferenceIds} is applied on the way in as well as out, so a
 * payload holding embedded artifacts loads as ids rather than half-flattening on the next save.
 *
 * Everything else is a straight copy, written out field by field rather than spread so a control the
 * form may gain later cannot leak into the payload unnoticed. `PUT /roles/{roleId}` is a full
 * replacement, so an omitted field is a cleared one — which is why `responsibleFor` had to be
 * modelled at all: until this revision the mapper did not carry it, so every save silently cleared
 * the seeded value.
 */
@Injectable({ providedIn: 'root' })
export class RoleDefinitionMapper implements BaseEntityMapper<RoleDefinition> {
  fromDto(dto: unknown): RoleDefinition {
    const source = dto as RoleDefinitionDto;
    return new RoleDefinition({
      id: source.id,
      name: source.name,
      description: source.description,
      responsibleFor: toReferenceIds(source.responsibleFor),
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
      responsibleFor: toReferenceIds(entity.responsibleFor),
      entityRoleId: entity.entityRoleId,
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}
