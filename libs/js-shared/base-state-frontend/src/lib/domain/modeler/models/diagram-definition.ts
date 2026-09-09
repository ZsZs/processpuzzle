import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Frontend model of the diagram layer of `base-state-api.yaml`: where the states and transitions of one
 * state machine sit on the modeler's canvas.
 *
 * Purely presentational, and a resource of its own rather than fields on `StateMachineDefinition`. Two
 * reasons, both practical: dragging a node would otherwise go through the machine's whole-document
 * `PUT /state-machines/{entityName}`, putting the topology at risk on a cosmetic gesture; and the two are
 * written by different gestures at very different rates, so one optimistic-lock version would make an
 * arrangement and an edit collide for no reason.
 *
 * A machine with no layout is the normal starting point, not an error: `GET /diagrams/{entityName}` answers
 * 404, and the modeler falls back to `DagreLayoutService` / `ElkLayoutService`. Field names are the
 * contract's throughout, and the shapes mirror ng-diagram's own `Point`, `Size` and `Edge` so that a
 * loaded layout can be handed to `initializeModel` with no reinterpretation.
 *
 * Deliberately absent, to be added when a feature needs them rather than speculatively: node `angle` (a
 * state is never rotated), `zOrder`, `autoSize`, and group membership — there are no nested or parallel
 * states in this version, so a node is never inside another node.
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
 * Where one `State` sits on the canvas.
 *
 * An array of keyed rows rather than a map keyed by `stateKey`, matching how `StateMachineDefinition`
 * carries its `states` and `transitions`: the key stays visible inside the row, which is what makes an
 * exported layout readable. The modeler indexes them into a `Map` on load when it needs lookup.
 *
 * `stateKey` is not validated against the machine's declared states, on either side of the wire. A
 * machine's topology may drop a state, and a row naming a key nothing renders any more is harmless — it
 * is ignored, and the next save prunes it.
 */
export class NodeLayout implements BaseEntity {
  /**
   * Declared, never assigned. `stateKey` identifies a row; the contract gives it no `id`. Same reason as
   * `State.id` in `state-machine-definition.ts` — `declare` emits nothing, so the payload stays exactly
   * the shape the schema describes.
   */
  declare readonly id?: string;

  /** The `State.key` this row positions. */
  stateKey: string;
  position: Point;
  /** Absent when the node is auto-sized by its content, which is the default. */
  size?: NodeSize;

  constructor(init: Partial<NodeLayout> = {}) {
    this.stateKey = init.stateKey ?? '';
    this.position = init.position ?? new Point();
    this.size = init.size;
  }
}

/**
 * How one `Transition` is drawn.
 *
 * The port anchors matter as much as the waypoints do: ng-diagram picks an anchor when an edge is created,
 * so an edge whose ports are not persisted is re-anchored on the next load and the diagram reopens with
 * visibly different geometry from the one the user arranged.
 */
export class EdgeLayout implements BaseEntity {
  /** Declared, never assigned: `transitionKey` identifies a row. Same reason as {@link NodeLayout.id}. */
  declare readonly id?: string;

  /** The `Transition.key` this row routes. Not validated against the machine — see {@link NodeLayout}. */
  transitionKey: string;
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

  constructor(init: Partial<EdgeLayout> = {}) {
    this.transitionKey = init.transitionKey ?? '';
    // An empty array rather than undefined, so a waypoint always has something to append to.
    this.points = init.points ?? [];
    this.sourcePort = init.sourcePort;
    this.targetPort = init.targetPort;
    this.routing = init.routing;
  }
}

/**
 * Pan offset and zoom of the canvas, persisted so that reopening a large machine returns to the part of it
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
 * The layout of one entity type's state machine. Bound 1:1 to `entityName` — the contract addresses a
 * layout by that name rather than by a key of its own, exactly as the machine itself is addressed — so
 * {@link id} is a *mirror* of {@link entityName} and not an independent field, the same arrangement
 * `StateMachineDefinition` uses and for the same reason: every single-record URL `BaseEntityRestService`
 * builds comes from `id`.
 *
 * Written with `PUT /diagrams/{entityName}`, which upserts: 201 the first time an entityName is arranged,
 * 200 every time after. There is no create/replace pair to choose between, so a "save layout" gesture
 * never has to know whether this machine has been arranged before.
 */
export class DiagramDefinition implements BaseEntity {
  /** Mirror of {@link entityName}; maintained by the mapper and never edited. See the class comment. */
  id: string;
  /** The entity type whose state machine this lays out. The URL is authoritative; this only repeats it. */
  entityName: string;
  nodes: NodeLayout[];
  edges: EdgeLayout[];
  /** Absent until the canvas has been panned or zoomed. */
  viewport?: DiagramViewport;
  // region server-assigned
  orgKey: string | undefined;
  version: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  // endregion

  constructor(init: Partial<DiagramDefinition> = {}) {
    this.entityName = init.entityName ?? '';
    this.id = init.id ?? this.entityName;
    // Empty arrays rather than undefined: a machine opened but never arranged is the normal starting
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
