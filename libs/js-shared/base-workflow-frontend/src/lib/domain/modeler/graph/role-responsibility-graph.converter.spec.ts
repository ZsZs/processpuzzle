import { describe, expect, it } from 'vitest';
import { ArtifactDefinition, ArtifactType } from '../../definition/artifact-definition';
import { RoleDefinition } from '../../definition/role-definition';
import { RoleResponsibilityGraphConverter } from './role-responsibility-graph.converter';

describe('RoleResponsibilityGraphConverter', () => {
  const clerk = new RoleDefinition({ id: 'clerk', name: 'Order Clerk', description: 'Enters orders.', responsibleFor: ['order-entity'] });
  const manager = new RoleDefinition({ id: 'manager', name: 'Order Manager', responsibleFor: ['fulfillment-invoice'] });
  const orderEntity = new ArtifactDefinition({ id: 'order-entity', name: 'Order Entity', description: 'The order.', artifactType: ArtifactType.ENTITY });
  const invoice = new ArtifactDefinition({ id: 'fulfillment-invoice', name: 'Fulfillment Invoice', artifactType: ArtifactType.DOCUMENT });

  it('draws a node per role and per owned artifact, joined by the responsibility', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph([clerk, manager], [orderEntity, invoice]);

    expect(graph.nodes.map((node) => node.id)).toEqual(['role:clerk', 'role:manager', 'artifact:order-entity', 'artifact:fulfillment-invoice']);
    expect(graph.edges.map((edge) => [edge.source, edge.target])).toEqual([
      ['role:clerk', 'artifact:order-entity'],
      ['role:manager', 'artifact:fulfillment-invoice'],
    ]);
  });

  it('names each element and says which kind it is', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph([clerk], [orderEntity]);

    expect(graph.nodes.map((node) => [node.data.kind, node.data.label, node.data.description])).toEqual([
      ['role', 'Order Clerk', 'Enters orders.'],
      ['artifact', 'Order Entity', 'The order.'],
    ]);
  });

  // The generated forms leave `name` optional, and a node with no caption at all would be unreadable.
  it('falls back to the id of an element with no name', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph(
      [new RoleDefinition({ id: 'auditor', responsibleFor: ['audit-log'] })],
      [new ArtifactDefinition({ id: 'audit-log' })],
    );

    expect(graph.nodes.map((node) => node.data.label)).toEqual(['auditor', 'audit-log']);
  });

  it('marks the role the diagram was opened from, and only that one', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph([clerk, manager], [orderEntity, invoice], 'manager');

    expect(graph.nodes.filter((node) => node.data.highlighted).map((node) => node.id)).toEqual(['role:manager']);
  });

  it('marks nothing when the diagram was opened from a role it does not hold', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph([clerk], [orderEntity], 'not-a-role');

    expect(graph.nodes.some((node) => node.data.highlighted)).toBe(false);
  });

  /**
   * Nothing enforces that an id in `responsibleFor` resolves — deleting an artifact a role still names is
   * how it stops resolving — and dropping the reference would show the role as owning less than it claims.
   */
  it('draws an artifact the catalog does not contain, flagged and labelled by its id', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph([new RoleDefinition({ id: 'clerk', responsibleFor: ['deleted-artifact'] })], []);

    expect(graph.nodes.find((node) => node.id === 'artifact:deleted-artifact')?.data).toMatchObject({
      kind: 'artifact',
      label: 'deleted-artifact',
      unresolved: true,
    });
    expect(graph.edges.map((edge) => edge.target)).toEqual(['artifact:deleted-artifact']);
  });

  it('does not flag an artifact it resolved', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph([clerk], [orderEntity]);

    expect(graph.nodes.every((node) => !node.data.unresolved)).toBe(true);
  });

  // Two owners of one artifact is legitimate — ownership of an outcome is not exclusive by contract — and
  // it is one node with two edges into it, not a duplicate id ng-diagram would reject.
  it('draws an artifact two roles own once', () => {
    const second = new RoleDefinition({ id: 'manager', name: 'Order Manager', responsibleFor: ['order-entity'] });
    const graph = RoleResponsibilityGraphConverter.toGraph([clerk, second], [orderEntity]);

    expect(graph.nodes.filter((node) => node.id === 'artifact:order-entity')).toHaveLength(1);
    expect(graph.edges.map((edge) => edge.source)).toEqual(['role:clerk', 'role:manager']);
  });

  it('draws one edge for a role that names the same artifact twice', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph([new RoleDefinition({ id: 'clerk', responsibleFor: ['order-entity', 'order-entity'] })], [orderEntity]);

    expect(graph.edges).toHaveLength(1);
    expect(graph.nodes).toHaveLength(2);
  });

  // A role owning nothing is a fact about the organisation, so it is on the diagram as an isolated node.
  it('draws a role with no responsibilities', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph([new RoleDefinition({ id: 'observer', name: 'Observer' })], [orderEntity]);

    expect(graph.nodes.map((node) => node.id)).toEqual(['role:observer']);
    expect(graph.edges).toEqual([]);
  });

  it('draws nothing at all when the organisation has no roles', () => {
    expect(RoleResponsibilityGraphConverter.toGraph([], [orderEntity])).toEqual({ nodes: [], edges: [] });
  });

  /**
   * The ids are only unique within a catalog — `/roles/{id}` and `/artifacts/{id}` are separate resources —
   * so a tenant may have a role and an artifact both called `order`. Unprefixed they would be one node and
   * the role would be drawn as responsible for itself.
   */
  it('keeps a role and an artifact of the same id apart', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph(
      [new RoleDefinition({ id: 'order', name: 'Order Role', responsibleFor: ['order'] })],
      [new ArtifactDefinition({ id: 'order', name: 'Order Artifact' })],
    );

    expect(graph.nodes.map((node) => node.id)).toEqual(['role:order', 'artifact:order']);
    expect(graph.edges[0]).toMatchObject({ source: 'role:order', target: 'artifact:order' });
  });

  // The layout service is what places the graph; a converter that guessed positions would make an
  // unarranged diagram indistinguishable from one arranged badly.
  it('leaves every node unplaced, for the layout service to place', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph([clerk, manager], [orderEntity, invoice]);

    expect(graph.nodes.every((node) => node.position.x === 0 && node.position.y === 0)).toBe(true);
  });

  it('draws every element through the one element template', () => {
    const graph = RoleResponsibilityGraphConverter.toGraph([clerk], [orderEntity]);

    expect(new Set(graph.nodes.map((node) => node.type))).toEqual(new Set(['ppWorkflowElement']));
  });
});
