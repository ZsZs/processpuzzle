import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { ArtifactDefinition, ArtifactType } from './artifact-definition';

/** The wire shape of `ArtifactDefinition`, exactly as it travels. */
interface ArtifactDefinitionDto {
  id?: string;
  name?: string;
  description?: string;
  type?: ArtifactType;
  entityTypeId?: string;
  stateMachineId?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Translates between the `ArtifactDefinition` DTO of `base-workflow-api.yaml` and the entity the
 * generated screens work with. A straight copy — the model's field names are the contract's —
 * written out field by field so a control the form may gain later cannot leak into the
 * full-replacement `PUT /artifacts/{artifactId}` unnoticed.
 */
@Injectable({ providedIn: 'root' })
export class ArtifactDefinitionMapper implements BaseEntityMapper<ArtifactDefinition> {
  fromDto(dto: unknown): ArtifactDefinition {
    const source = dto as ArtifactDefinitionDto;
    return new ArtifactDefinition({
      id: source.id,
      name: source.name,
      description: source.description,
      type: source.type,
      entityTypeId: source.entityTypeId,
      stateMachineId: source.stateMachineId,
      version: source.version,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    });
  }

  toDto(entity: ArtifactDefinition): ArtifactDefinitionDto {
    return {
      id: entity.id,
      name: entity.name,
      description: entity.description,
      type: entity.type,
      entityTypeId: entity.entityTypeId,
      stateMachineId: entity.stateMachineId,
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}
