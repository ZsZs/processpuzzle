import { describe, expect, it } from 'vitest';
import { elementEdgeId, elementNodeId, laneNodeId, WORKFLOW_LANE_TYPE, WORKFLOW_NODE_TYPE, WORKFLOW_RELATION_EDGE_TYPE } from './workflow-graph';

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

describe('laneNodeId', () => {
  it('names a lane by the role that performs in it', () => {
    expect(laneNodeId('clerk')).toBe('lane:clerk');
  });

  /**
   * The reason a lane does not reuse the role's own node id. A graph may hold both — a role card in the
   * Roles perspective, a lane in the Workflows one — and one shared id would silently drop whichever
   * arrived second.
   */
  it('does not collide with the role’s own element node', () => {
    expect(laneNodeId('clerk')).not.toBe(elementNodeId('role', 'clerk'));
  });
});

describe('template keys', () => {
  // Three flat registries share one namespace per diagram, so the values have to differ from each other as
  // well as be prefixed against whatever another feature might register.
  it('are distinct from one another', () => {
    expect(new Set([WORKFLOW_NODE_TYPE, WORKFLOW_LANE_TYPE, WORKFLOW_RELATION_EDGE_TYPE]).size).toBe(3);
  });
});
