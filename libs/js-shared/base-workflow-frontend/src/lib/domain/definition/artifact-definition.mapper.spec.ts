import { describe, expect, it } from 'vitest';
import { ArtifactDefinition, ArtifactType } from './artifact-definition';
import { ArtifactDefinitionMapper } from './artifact-definition.mapper';
import { ARTIFACT_DEFINITION_DTO, OTHER_ARTIFACT_DEFINITION_DTO } from './test-artifact-definition';

describe('ArtifactDefinitionMapper', () => {
  const mapper = new ArtifactDefinitionMapper();

  describe('fromDto', () => {
    it('reads the seeded artifact, bindings into the other features included', () => {
      const artifact = mapper.fromDto(ARTIFACT_DEFINITION_DTO);

      expect(artifact).toBeInstanceOf(ArtifactDefinition);
      expect(artifact.id).toBe('order-entity');
      expect(artifact.type).toBe(ArtifactType.ENTITY);
      expect(artifact.entityTypeId).toBe('order');
      expect(artifact.stateMachineId).toBe('order');
    });

    it('leaves the bindings absent for an artifact nothing governs', () => {
      const artifact = mapper.fromDto(OTHER_ARTIFACT_DEFINITION_DTO);

      expect(artifact.type).toBe(ArtifactType.DELIVERABLE);
      expect(artifact.entityTypeId).toBeUndefined();
      expect(artifact.stateMachineId).toBeUndefined();
    });
  });

  describe('toDto', () => {
    it('round-trips the seeded artifact', () => {
      expect(mapper.toDto(mapper.fromDto(ARTIFACT_DEFINITION_DTO))).toEqual(ARTIFACT_DEFINITION_DTO);
    });

    // Listed field by field rather than spread, so a control the form may gain later cannot leak into
    // the full-replacement PUT unnoticed.
    it('emits exactly the contract’s fields and nothing else', () => {
      const dto = mapper.toDto(mapper.fromDto(ARTIFACT_DEFINITION_DTO));

      expect(Object.keys(dto).sort()).toEqual(['createdAt', 'description', 'entityTypeId', 'id', 'name', 'stateMachineId', 'type', 'updatedAt', 'version']);
    });
  });
});
