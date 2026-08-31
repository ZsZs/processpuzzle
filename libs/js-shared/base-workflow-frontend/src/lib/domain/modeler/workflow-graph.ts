import { Edge, Node } from 'ng-diagram';

/**
 * The ng-diagram graph of one modeler perspective, and the vocabulary every perspective shares.
 *
 * Nothing here is a model of its own. The nodes and edges are ng-diagram's, so a converted graph can be
 * handed straight to `initializeModel`, and the two `data` shapes below are the only thing this module
 * adds — they are how base-workflow's own vocabulary survives the trip through a generic diagram library.
 *
 * Deliberately free of any one perspective. The Roles modeler is the first of three — Tasks and Workflows
 * follow — and what differs between them is which elements are drawn and what an edge between two of them
 * means, not how an element is represented. That difference lives in a converter
 * ({@link RoleResponsibilityGraphConverter} is the first), and everything else in `domain/modeler` and
 * `feature/modeler` is shared.
 */

/**
 * The `type` every modeler node carries, and the key `NgDiagramNodeTemplateMap` resolves
 * {@link WorkflowElementNodeComponent} by. Prefixed, because the map is one flat registry per diagram and
 * `element` is a name any feature might plausibly claim.
 *
 * One type for all five kinds rather than one per kind: every element is drawn the same way — its symbol
 * and its name — and {@link WorkflowNodeData.kind} is what picks the symbol. Five registrations would be
 * five templates that had to be kept looking alike.
 */
export const WORKFLOW_NODE_TYPE = 'ppWorkflowElement';

/**
 * What a node stands for, which is also which symbol it is drawn with — the five files in
 * `src/assets/modeler`. See {@link modelerIconUrl}.
 *
 * The five are base-workflow's routable aggregates minus the instance layer: a modeler draws what a tenant
 * *authors*. A run is monitored on the generated instance screens, and drawing it would need a different
 * vocabulary again (a step that has finished, a task waiting on someone).
 */
export type WorkflowElementKind = 'role' | 'artifact' | 'task' | 'tool' | 'workflow';

/**
 * What a modeler node carries in ng-diagram's `data`.
 *
 * `label` and `description` rather than the domain object itself, which is where this departs from
 * base-state's `StateNodeData`: that modeler *edits* the state behind a node, so its properties panel needs
 * the whole `State`. These diagrams are read-only, and a node whose data is already what the template draws
 * is a node the same template can draw whatever aggregate it came from — which is what lets one node
 * component serve all five kinds.
 */
export interface WorkflowNodeData {
  kind: WorkflowElementKind;
  /** What the element is called: its `name`, or its id when it has no name. */
  label: string;
  /** Shown under the label when the element has one. */
  description?: string;
  /**
   * True for the one element the diagram was opened *from* — the role whose Modeler tab this is. Marks it
   * without changing what is drawn: the whole organisation is on screen either way.
   */
  highlighted?: boolean;
  /**
   * True for an element that exists only as a reference: an id named by another element that the catalog it
   * should live in does not contain. Drawn rather than dropped, because a dangling reference is a fact
   * about the model worth seeing — and silently omitting it would show a role as responsible for less than
   * it claims.
   */
  unresolved?: boolean;
}

/**
 * What a modeler edge carries in ng-diagram's `data`.
 *
 * `label` is optional and unused by the Roles perspective: a line from a role to an artifact already reads
 * as responsibility, and the one relation on screen needs no naming. A Task perspective, where an edge is
 * an input, an output or a tool call, is what it is here for.
 */
export interface WorkflowEdgeData {
  label?: string;
}

export type WorkflowNode = Node<WorkflowNodeData>;
export type WorkflowEdge = Edge<WorkflowEdgeData>;

/**
 * A converted perspective, ready for `initializeModel` once the nodes have been through
 * {@link WorkflowLayoutService}.
 *
 * No viewport and no "which nodes are unplaced", both of which `StateMachineGraph` carries: these diagrams
 * persist no arrangement, so every node is placed by the layout service on every build and there is no
 * saved position for one to be missing from.
 */
export interface WorkflowGraph {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
}

/**
 * The node id of one element, which is its kind and its own id.
 *
 * Prefixed by kind because the ids are only unique *within* a catalog: `/roles/{roleId}` and
 * `/artifacts/{artifactId}` are separate resources, so a tenant may perfectly well have a role and an
 * artifact both called `order`. Unprefixed, the two would be one node and the diagram would draw the role
 * as responsible for itself.
 */
export function elementNodeId(kind: WorkflowElementKind, id: string): string {
  return `${kind}:${id}`;
}

/**
 * The edge id of one relation, derived from the two node ids it joins.
 *
 * Derived rather than generated, so rebuilding the graph — which every reload and every catalog change
 * does — produces the same ids as before. A generated id would make ng-diagram treat an unchanged edge as
 * a new one.
 */
export function elementEdgeId(sourceNodeId: string, targetNodeId: string): string {
  return `${sourceNodeId}->${targetNodeId}`;
}
