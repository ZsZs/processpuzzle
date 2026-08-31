import { describe, expect, it } from 'vitest';
import { elementEdgeId, elementNodeId } from './workflow-graph';

describe('elementNodeId', () => {
  it('names a node by its kind and its own id', () => {
    expect(elementNodeId('role', 'clerk')).toBe('role:clerk');
    expect(elementNodeId('artifact', 'order-entity')).toBe('artifact:order-entity');
  });

  /**
   * The reason for the prefix at all. `/roles/{roleId}` and `/artifacts/{artifactId}` are separate
   * resources, so the same id in both is legal — and unprefixed the two would collapse into one node,
   * drawing the role as responsible for itself.
   */
  it('tells a role and an artifact of the same id apart', () => {
    expect(elementNodeId('role', 'order')).not.toBe(elementNodeId('artifact', 'order'));
  });
});

describe('elementEdgeId', () => {
  it('names an edge by the two nodes it joins', () => {
    expect(elementEdgeId('role:clerk', 'artifact:order-entity')).toBe('role:clerk->artifact:order-entity');
  });

  // Derived rather than generated, so a rebuild of the same graph produces the same ids and ng-diagram
  // does not treat an unchanged edge as a new one.
  it('is stable across calls', () => {
    expect(elementEdgeId('role:clerk', 'artifact:order-entity')).toBe(elementEdgeId('role:clerk', 'artifact:order-entity'));
  });

  it('distinguishes the two directions', () => {
    expect(elementEdgeId('a', 'b')).not.toBe(elementEdgeId('b', 'a'));
  });
});
