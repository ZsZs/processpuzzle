import { describe, expect, it } from 'vitest';
import { ArtifactDefinition, ArtifactType } from './artifact-definition';

describe('ArtifactDefinition', () => {
  it('mints a blank record a New can open a form on', () => {
    const artifact = new ArtifactDefinition();

    expect(artifact.id).toBe('');
    expect(artifact.name).toBe('');
    expect(artifact.artifactType).toBeUndefined();
  });

  // Both name resources owned by other features, and both are optional: an artifact whose lifecycle
  // nothing governs simply leaves them unset.
  it('leaves the cross-feature bindings undefined when nothing set them', () => {
    const artifact = new ArtifactDefinition({ id: 'fulfillment-invoice', artifactType: ArtifactType.DOCUMENT });

    expect(artifact.artifactTypeId).toBeUndefined();
    expect(artifact.stateMachineId).toBeUndefined();
  });

  it('leaves the server-assigned fields undefined until a backend fills them', () => {
    const artifact = new ArtifactDefinition({ id: 'order-entity' });

    expect(artifact.version).toBeUndefined();
    expect(artifact.createdAt).toBeUndefined();
    expect(artifact.updatedAt).toBeUndefined();
  });

  // Asserted against the contract's own list rather than a copy of whatever the enum happens to hold:
  // it carried SPEM's older ARTIFACT / DELIVERABLE / OUTCOME here, so the dropdown offered three values
  // the backend rejects while DOCUMENT and WIDGET — both used by the seed — could not be chosen at all.
  it('mirrors the contract enum exactly', () => {
    expect(Object.keys(ArtifactType)).toEqual(['DOCUMENT', 'ENTITY', 'WIDGET']);
  });
});
