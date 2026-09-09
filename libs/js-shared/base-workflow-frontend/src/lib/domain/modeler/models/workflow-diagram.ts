import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Frontend model of the diagram layer of `base-workflow-api.yaml`: where one workflow's tasks, lanes,
 * work products and tools sit on the modeler's canvas.
 *
 * Purely presentational, and a resource of its own rather than fields on `Workflow`. Two reasons, both
 * practical: dragging a task would otherwise go through the workflow's whole-document
 * `PUT /workflows/{workflowId}`, putting its composition at risk on a cosmetic gesture; and the two are
 * written by different gestures at very different rates, so one optimistic-lock version would make an
 * arrangement and an edit collide for no reason.
 *
 * A workflow with no layout is the normal starting point, not an error: `GET /workflow-diagrams/{workflowId}`
 * answers 404, and the modeler keeps the automatic `SwimlaneLayoutService` arrangement. Field names are the
 * contract's throughout, and the shapes mirror ng-diagram's own `Point`, `Size` and `Edge` so that a loaded
 * layout can be applied to a placed graph with no reinterpretation.
 *
 * The one place this departs from base-state's equivalent is the **key**. A `NodeLayout` there is keyed by
 * `stateKey`, a domain key; here a node stands for a task, a lane, an artifact or a tool, whose ids are only
 * unique within their own catalog — so the row is keyed by the *diagram* node id the modeler mints
 * (`task:<id>`, `lane:<roleId>`, see `elementNodeId` and `laneNodeId`), and an edge's by the id derived from
 * its two ends.
 */

/** A point in diagram coordinates — ng-diagram's own `Point`. */
export class Point {
  x: number;
  y: number;

  constructor(init: Partial<Point> = {}) {
    this.x = init.x ?? 0;
    this.y = init.y ?? 0;
  }
}

/**
 * Node dimensions in diagram coordinates — ng-diagram's own `Size`.
 *
 * Named `NodeSize` to match the contract schema, which could not be called `Size`: the generated Java
 * models share a package with the Jakarta validation annotations the generator emits, so a `Size` schema
 * shadows `jakarta.validation.constraints.Size`.
 */
export class NodeSize {
  width: number;
  height: number;

  constructor(init: Partial<NodeSize> = {}) {
    this.width = init.width ?? 0;
    this.height = init.height ?? 0;
  }
}

/**
 * Where one modeler node sits on the canvas.
 *
 * `nodeId` is not validated against the workflow, on either side of the wire. A workflow may drop a task,
 * and a row naming a node nothing renders any more is harmless — it is ignored, and the next save prunes it.
 *
 * `size` matters more here than it does in base-state, where it is usually absent: the swimlane layout states
 * a size on every node it places, and a **lane** is a band whose height is computed from the tasks it holds.
 * A resized lane that did not persist its size would snap back on the next load.
 */
export class DiagramNodeLayout implements BaseEntity {
  /**
   * Declared, never assigned. `nodeId` identifies a row; the contract gives it no `id`. `declare` emits
   * nothing, so the payload stays exactly the shape the schema describes.
   */
  declare readonly id?: string;

  /** The diagram node this row positions — `task:<id>`, `lane:<roleId>`, `artifact:<id>`, `tool:<id>`. */
  nodeId: string;
  position: Point;
  /** Absent means the node keeps whatever size the automatic layout computed for it. */
  size?: NodeSize;

  constructor(init: Partial<DiagramNodeLayout> = {}) {
    this.nodeId = init.nodeId ?? '';
    this.position = init.position ?? new Point();
    this.size = init.size;
  }
}

/**
 * How one relation is drawn.
 *
 * The port anchors matter as much as the waypoints do: the flow converter pins them per relation kind — the
 * sequence flow runs left to right, data and tool lines run vertically — so an edge whose ports are not
 * persisted is re-anchored on the next load and a data line may leave a task by its right edge and cross the
 * whole chain.
 */
export class DiagramEdgeLayout implements BaseEntity {
  /** Declared, never assigned: `edgeId` identifies a row. Same reason as {@link DiagramNodeLayout.id}. */
  declare readonly id?: string;

  /** The diagram edge this row routes. Not validated against the workflow — see {@link DiagramNodeLayout}. */
  edgeId: string;
  /**
   * Intermediate waypoints, in order — ng-diagram's `Edge.points`. Empty means the edge is routed
   * automatically between its two ports.
   */
  points: Point[];
  /** The port on the source node the edge leaves from, e.g. `port-right`. */
  sourcePort?: string;
  /** The port on the target node the edge arrives at, e.g. `port-left`. */
  targetPort?: string;
  /** The edge's routing mode as the user chose it — ng-diagram's `Edge.routing`. Opaque to the backend. */
  routing?: string;

  constructor(init: Partial<DiagramEdgeLayout> = {}) {
    this.edgeId = init.edgeId ?? '';
    // An empty array rather than undefined, so a waypoint always has something to append to.
    this.points = init.points ?? [];
    this.sourcePort = init.sourcePort;
    this.targetPort = init.targetPort;
    this.routing = init.routing;
  }
}

/**
 * Pan offset and zoom of the canvas, persisted so that reopening a large workflow returns to the part of it
 * the user was working on rather than to whatever an automatic fit chooses.
 */
export class DiagramViewport {
  x: number;
  y: number;
  /** Zoom factor, `1` being 100%. */
  scale: number;

  constructor(init: Partial<DiagramViewport> = {}) {
    this.x = init.x ?? 0;
    this.y = init.y ?? 0;
    this.scale = init.scale ?? 1;
  }
}

/**
 * The layout of one workflow's modeler diagram. Bound 1:1 to `workflowId` — the contract addresses a layout
 * by that id rather than by a key of its own — so {@link id} is a *mirror* of {@link workflowId} and not an
 * independent field. That is the same arrangement base-state's `DiagramDefinition` uses and for the same
 * reason: every single-record URL `BaseEntityRestService` builds comes from `id`.
 *
 * Written with `PUT /workflow-diagrams/{workflowId}`, which upserts: 201 the first time a workflow is
 * arranged, 200 every time after. There is no create/replace pair to choose between, so a "save layout"
 * gesture never has to know whether this workflow has been arranged before.
 */
export class WorkflowDiagram implements BaseEntity {
  /** Mirror of {@link workflowId}; maintained by the mapper and never edited. See the class comment. */
  id: string;
  /** The workflow this lays out. The URL is authoritative; this only repeats it. */
  workflowId: string;
  nodes: DiagramNodeLayout[];
  edges: DiagramEdgeLayout[];
  /** Absent until the canvas has been panned or zoomed. */
  viewport?: DiagramViewport;
  // region server-assigned
  orgKey: string | undefined;
  version: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  // endregion

  constructor(init: Partial<WorkflowDiagram> = {}) {
    this.workflowId = init.workflowId ?? '';
    this.id = init.id ?? this.workflowId;
    // Empty arrays rather than undefined: a workflow opened but never arranged is the normal starting
    // point, and the canvas appends to these as the user drags.
    this.nodes = init.nodes ?? [];
    this.edges = init.edges ?? [];
    this.viewport = init.viewport;
    this.orgKey = init.orgKey;
    this.version = init.version;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
