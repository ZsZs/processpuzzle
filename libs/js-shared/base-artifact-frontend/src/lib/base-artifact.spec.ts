import { describe, expect, it } from 'vitest';
import { BaseArtifact, DEFAULT_ARTIFACT_CONTENT_TYPE } from './base-artifact';

describe('BaseArtifact', () => {
  it('exposes the name passed to the constructor', () => {
    const artifact = new BaseArtifact('invoice.pdf');

    expect(artifact.name).toBe('invoice.pdf');
  });

  it('defaults the content type to the binary media type when not provided', () => {
    const artifact = new BaseArtifact('invoice.pdf');

    expect(artifact.contentType).toBe(DEFAULT_ARTIFACT_CONTENT_TYPE);
    expect(artifact.isBinary).toBe(true);
  });

  it('reports a declared content type as not binary', () => {
    const artifact = new BaseArtifact('invoice.pdf', 'application/pdf');

    expect(artifact.isBinary).toBe(false);
  });

  it('appends the content type to the description when one is declared', () => {
    const artifact = new BaseArtifact('invoice.pdf', 'application/pdf');

    expect(artifact.describe()).toBe('invoice.pdf (application/pdf)');
  });

  it('returns only the name when the artifact is binary', () => {
    const artifact = new BaseArtifact('scan.bin');

    expect(artifact.describe()).toBe('scan.bin');
  });
});
