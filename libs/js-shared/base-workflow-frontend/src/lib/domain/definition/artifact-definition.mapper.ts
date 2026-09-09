import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { ArtifactDefinition, ArtifactType } from './artifact-definition';

/** The wire shape of `ArtifactDefinition`, exactly as it travels. */
interface ArtifactDefinitionDto {
  id?: string;
  name?: string;
  description?: string;
  artifactType?: ArtifactType;
  artifactTypeId?: string;
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
 *
 * The names were *not* the contract's until this revision: the DTO said `type` and `entityTypeId`
 * where the schema and the backend column both say `artifactType` and `artifactTypeId`, so an
 * artifact's kind and its backing type were dropped in both directions. Hence the field-by-field
 * spelling being worth keeping even for a copy.
 */
@Injectable({ providedIn: 'root' })
export class ArtifactDefinitionMapper implements BaseEntityMapper<ArtifactDefinition> {
  fromDto(dto: unknown): ArtifactDefinition {
    const source = dto as ArtifactDefinitionDto;
    return new ArtifactDefinition({
      id: source.id,
      name: source.name,
      description: source.description,
      artifactType: source.artifactType,
      artifactTypeId: source.artifactTypeId,
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
      artifactType: entity.artifactType,
      artifactTypeId: entity.artifactTypeId,
      stateMachineId: entity.stateMachineId,
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}
