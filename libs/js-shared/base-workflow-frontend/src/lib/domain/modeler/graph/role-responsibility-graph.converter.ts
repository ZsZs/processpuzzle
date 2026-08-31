import { ArtifactDefinition } from '../../definition/artifact-definition';
import { RoleDefinition } from '../../definition/role-definition';
import { elementEdgeId, elementNodeId, WORKFLOW_NODE_TYPE, WorkflowEdge, WorkflowGraph, WorkflowNode } from '../workflow-graph';

/**
 * The Roles perspective: who the organisation's roles are, and which artifacts each one owns.
 *
 * The first of the three perspectives, and the whole of what makes it *the Roles one* — everything else in
 * `domain/modeler` and `feature/modeler` is kind-agnostic. A Tasks perspective is another converter beside
 * this one, not a change to anything below it.
 *
 * The relation drawn is `RoleDefinition.responsibleFor`, which is why the direction is role → artifact:
 * ownership is stated by the role, and the artifact catalog knows nothing about who owns what.
 *
 * Two rules are decisions rather than mapping.
 *
 * **Only referenced artifacts are drawn.** The catalog holds everything a task may touch; an overview of
 * responsibility that also drew every unowned artifact would bury the relation it is about. An artifact
 * nobody owns is visible on its own generated list.
 *
 * **A dangling reference is drawn, not dropped.** An id in `responsibleFor` that the catalog does not
 * contain becomes an `unresolved` node labelled by the raw id. Omitting it would show the role as
 * responsible for less than it claims, and the diagram would look correct while hiding the one thing about
 * the model that needs fixing.
 */
export class RoleResponsibilityGraphConverter {
  /**
   * Builds the graph of `roles` and the artifacts they own, out of the two catalogs as loaded.
   *
   * `highlightedRoleId` marks the role whose Modeler tab this is — the tab mounts per row, and the whole
   * organisation is on screen either way, so the id it was opened from is a mark rather than a filter. An id
   * naming no role marks nothing, which is what a tab opened before the roles have arrived does.
   */
  static toGraph(roles: RoleDefinition[], artifacts: ArtifactDefinition[], highlightedRoleId?: string): WorkflowGraph {
    // Indexed once rather than searched per reference: every role names artifacts, so a per-reference
    // `find` over the catalog would be quadratic in two lists that grow together.
    const artifactsById = new Map(artifacts.map((artifact) => [artifact.id, artifact]));
    const nodes: WorkflowNode[] = roles.map((role) => toRoleNode(role, role.id === highlightedRoleId));
    const edges: WorkflowEdge[] = [];
    // Two roles may own the same artifact, and it is one node with two edges into it.
    const drawnArtifactIds = new Set<string>();

    roles.forEach((role) => {
      const roleNodeId = elementNodeId('role', role.id);
      (role.responsibleFor ?? []).forEach((artifactId) => {
        const artifactNodeId = elementNodeId('artifact', artifactId);
        if (!drawnArtifactIds.has(artifactId)) {
          drawnArtifactIds.add(artifactId);
          nodes.push(toArtifactNode(artifactId, artifactsById.get(artifactId)));
        }
        // Keyed by the two ends, so a role naming the same artifact twice is one edge — ng-diagram would
        // otherwise draw two lines on top of each other and hold two nodes with the same id.
        const edgeId = elementEdgeId(roleNodeId, artifactNodeId);
        if (!edges.some((edge) => edge.id === edgeId)) edges.push({ id: edgeId, source: roleNodeId, target: artifactNodeId, data: {} });
      });
    });

    return { nodes, edges };
  }
}

// region private helper functions
/**
 * The origin is a placeholder: ng-diagram requires a position and this converter lays nothing out — see
 * `WorkflowLayoutService`, which is what places every node before the graph reaches the canvas. `autoSize`
 * lets a node whose description wraps to two lines grow, rather than clipping it to the estimate the layout
 * spaced it by.
 */
function toRoleNode(role: RoleDefinition, highlighted: boolean): WorkflowNode {
  return {
    id: elementNodeId('role', role.id),
    type: WORKFLOW_NODE_TYPE,
    position: { x: 0, y: 0 },
    autoSize: true,
    data: { kind: 'role', label: role.name || role.id, description: role.description, highlighted },
  };
}

/**
 * `artifact` is the catalog entry when there is one. There need not be: `responsibleFor` holds ids, nothing
 * enforces that they resolve, and an artifact deleted while a role still names it is exactly how that
 * happens. Such a node is labelled by the id — the only name available — and flagged so the template can
 * draw it as the loose end it is.
 */
function toArtifactNode(artifactId: string, artifact?: ArtifactDefinition): WorkflowNode {
  return {
    id: elementNodeId('artifact', artifactId),
    type: WORKFLOW_NODE_TYPE,
    position: { x: 0, y: 0 },
    autoSize: true,
    data: {
      kind: 'artifact',
      label: artifact ? artifact.name || artifact.id : artifactId,
      description: artifact?.description,
      unresolved: artifact === undefined,
    },
  };
}
// endregion
