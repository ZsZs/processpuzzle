import { Edge, GroupNode, Node } from 'ng-diagram';

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
 * The `type` of a **lane** — the ng-diagram group node the Workflows perspective puts one of per performing
 * role, and the key {@link WorkflowLaneNodeComponent} is registered under.
 *
 * A second node type rather than a sixth {@link WorkflowElementKind}, because a lane is not drawn like an
 * element at all: it is a band the width of the whole diagram with a header down its left edge, and
 * ng-diagram resolves a group node through a template of its own shape
 * (`NgDiagramGroupNodeTemplate`). Its `data` is still {@link WorkflowNodeData} with `kind: 'role'`, so the
 * header's symbol and name come from the same two helpers as every other element's.
 */
export const WORKFLOW_LANE_TYPE = 'ppWorkflowLane';

/**
 * The `type` of an edge that says *which* relation it is, and the key
 * {@link WorkflowRelationEdgeComponent} is registered under in the canvas's edge template map.
 *
 * Only the Workflows perspective sets it. A Roles edge leaves `type` unset and so keeps ng-diagram's own
 * default edge template, exactly as before — one relation on screen needs no distinguishing.
 */
export const WORKFLOW_RELATION_EDGE_TYPE = 'ppWorkflowRelation';

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
  /**
   * The id of the catalog entry behind this node — the raw one, without the `kind:` prefix the node's own
   * id carries. Set so that the properties panel can name what it is showing without having to take a
   * composite node id back apart; the prefix is this module's business, not a panel's.
   *
   * Optional because a node's `data` is what a *template* draws, and neither the card nor the lane draws
   * an id. A perspective that has no panel need not fill it in.
   */
  elementId?: string;
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
 * What one edge *means*, which is also how it is drawn — see {@link WorkflowRelationEdgeComponent}.
 *
 * The split that matters is `sequence`/`implicit` against the rest: the first two are the workflow's
 * control flow and are drawn solid, everything else is a data or tool association and is drawn dashed, the
 * same distinction BPMN makes between a sequence flow and an association.
 *
 * - `sequence` — a `dependsOn` entry, the only flow relation the model states outright.
 * - `implicit` — the order two `parallel: false` siblings sharing a `dependsOn` set actually run in, which
 *   is their position in `Workflow.tasks` and exists nowhere as data. Drawn distinctly rather than as a
 *   plain sequence edge, so a reader can tell a declared dependency from one inferred from declaration
 *   order — the second changes when the rows are reordered and the first does not.
 * - `input` / `output` — a `TaskDefinition.inputs` / `.outputs` entry.
 * - `tool` — a `SERVICE_STEP`'s `toolDefinitionId`.
 * - `start` — a `Workflow.requiredArtifacts` entry feeding a task that depends on nothing. The model has no
 *   start element, and this is the nearest honest thing to one.
 */
export type WorkflowRelation = 'sequence' | 'implicit' | 'input' | 'output' | 'tool' | 'start';

/**
 * What a modeler edge carries in ng-diagram's `data`.
 *
 * Both fields are optional and both are unused by the Roles perspective: a line from a role to an artifact
 * already reads as responsibility, and the one relation on screen needs neither naming nor distinguishing.
 * The Workflows perspective, where an edge is a dependency, an input, an output or a tool call, is what
 * they are here for.
 */
export interface WorkflowEdgeData {
  /** Which relation this is. Absent leaves the edge on ng-diagram's default template. */
  relation?: WorkflowRelation;
  /** Written at the edge's midpoint when set. Used for the `ANY` join, the model's only gateway. */
  label?: string;
}

export type WorkflowNode = Node<WorkflowNodeData>;
export type WorkflowLaneNode = GroupNode<WorkflowNodeData>;
export type WorkflowEdge = Edge<WorkflowEdgeData>;

/**
 * Whether a node is a lane rather than an element.
 *
 * ng-diagram's `Node` is a union of `SimpleNode | GroupNode`, so `node.isGroup` does not type-check on one
 * — the property exists on only one arm. This is the same `'isGroup' in node` test the library narrows with
 * internally, named once here rather than repeated at each of the half-dozen places that has to sort the
 * lanes out from what is in them.
 */
export function isLaneNode(node: WorkflowNode): node is WorkflowLaneNode {
  return 'isGroup' in node;
}

/**
 * A converted perspective, ready for `initializeModel` once the nodes have been through
 * {@link WorkflowLayoutService}.
 *
 * No viewport and no "which nodes are unplaced", both of which `StateMachineGraph` carries — and both stay
 * absent now that the Workflows perspective does persist an arrangement. The viewport belongs to
 * `WorkflowDiagram`, the resource that stores it, rather than to the projection of the workflow; and there is
 * no unplaced list because the layout service places *every* node before a saved arrangement is applied over
 * it, so a node the arrangement does not mention is not unplaced, merely un-overridden.
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

/**
 * The node id of the lane a role performs its tasks in.
 *
 * Distinct from `elementNodeId('role', roleId)` on purpose. A lane and a role card are two different things
 * a diagram may hold at once — the Roles perspective draws the card, the Workflows perspective draws the
 * lane — and sharing one id would mean a graph could not carry both, which is the kind of constraint that
 * only shows up as a silently missing node.
 */
export function laneNodeId(roleId: string): string {
  return `lane:${roleId}`;
}
