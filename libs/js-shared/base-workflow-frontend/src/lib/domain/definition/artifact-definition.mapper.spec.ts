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
      expect(artifact.artifactType).toBe(ArtifactType.ENTITY);
      expect(artifact.artifactTypeId).toBe('order');
      expect(artifact.stateMachineId).toBe('order');
    });

    it('leaves the state machine absent for an artifact whose lifecycle nothing governs', () => {
      const artifact = mapper.fromDto(OTHER_ARTIFACT_DEFINITION_DTO);

      expect(artifact.artifactType).toBe(ArtifactType.DOCUMENT);
      expect(artifact.artifactTypeId).toBe('fulfillment-invoice');
      expect(artifact.stateMachineId).toBeUndefined();
    });

    // The regression the field-by-field DTO exists to catch: the mapper spelled these two `type` and
    // `entityTypeId` while the schema and the backend column both say `artifactType` and
    // `artifactTypeId`, so an artifact's kind and its backing type were dropped in both directions.
    it('reads the kind and the backing type under the contract’s own names', () => {
      const artifact = mapper.fromDto({ id: 'order-entity', type: 'DELIVERABLE', entityTypeId: 'stale' });

      expect(artifact.artifactType).toBeUndefined();
      expect(artifact.artifactTypeId).toBeUndefined();
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

      expect(Object.keys(dto).sort()).toEqual(['artifactType', 'artifactTypeId', 'createdAt', 'description', 'id', 'name', 'stateMachineId', 'updatedAt', 'version']);
    });
  });
});
