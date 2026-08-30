import { describe, expect, it } from 'vitest';
import { ArtifactDefinition, ArtifactType } from './artifact-definition';

describe('ArtifactDefinition', () => {
  it('mints a blank record a New can open a form on', () => {
    const artifact = new ArtifactDefinition();

    expect(artifact.id).toBe('');
    expect(artifact.name).toBe('');
    expect(artifact.type).toBeUndefined();
  });

  // Both name resources owned by other features, and both are optional: an artifact whose lifecycle
  // nothing governs simply leaves them unset.
  it('leaves the cross-feature bindings undefined when nothing set them', () => {
    const artifact = new ArtifactDefinition({ id: 'fulfillment-invoice', type: ArtifactType.DELIVERABLE });

    expect(artifact.entityTypeId).toBeUndefined();
    expect(artifact.stateMachineId).toBeUndefined();
  });

  it('leaves the server-assigned fields undefined until a backend fills them', () => {
    const artifact = new ArtifactDefinition({ id: 'order-entity' });

    expect(artifact.version).toBeUndefined();
    expect(artifact.createdAt).toBeUndefined();
    expect(artifact.updatedAt).toBeUndefined();
  });

  // Renamed from `WorkProductType` with its four values untouched: renaming the *values* would break
  // seed data for no gain, and `ARTIFACT` is still SPEM's own kind name.
  it('mirrors the contract enum, values unchanged by the rename', () => {
    expect(Object.keys(ArtifactType)).toEqual(['ARTIFACT', 'DELIVERABLE', 'OUTCOME', 'ENTITY']);
  });
});
