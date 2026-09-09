import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { DiagramDefinition, DiagramViewport, EdgeLayout, NodeLayout, NodeSize, Point } from '../models/diagram-definition';

interface PointDto {
  x?: number;
  y?: number;
}

interface NodeSizeDto {
  width?: number;
  height?: number;
}

interface NodeLayoutDto {
  stateKey?: string;
  position?: PointDto;
  size?: NodeSizeDto | null;
}

/** The three port/routing fields are `nullable: true` in the contract, so `null` is a value they arrive as. */
interface EdgeLayoutDto {
  transitionKey?: string;
  points?: PointDto[];
  sourcePort?: string | null;
  targetPort?: string | null;
  routing?: string | null;
}

interface DiagramViewportDto {
  x?: number;
  y?: number;
  scale?: number;
}

interface DiagramDefinitionDto {
  id?: string;
  entityName?: string;
  nodes?: NodeLayoutDto[];
  edges?: EdgeLayoutDto[];
  viewport?: DiagramViewportDto | null;
  orgKey?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Translates between the `DiagramDefinition` DTO of `base-state-api.yaml` and the layout the modeler
 * works with. Built the same way `StateMachineDefinitionMapper` is, and for the same three reasons.
 *
 * `id` is a **mirror of `entityName`** in both directions: the contract addresses a layout by
 * `entityName` and gives it no key of its own, while `BaseEntityRestService` builds every single-record
 * URL from `id`. `toDto` sends both — `entityName` because that is the contract's field, `id` because
 * json-server keys a record by it. The Spring backend ignores the extra property and treats the path
 * segment as authoritative, exactly as the contract says.
 *
 * The nested rows are **mapped element by element** rather than passed through, so that a row arriving
 * without a `position` still reaches the canvas renderable instead of throwing on the first read of
 * `position.x`.
 *
 * `PUT /diagrams/{entityName}` is a **full replacement**, so `toDto` emits `nodes` and `edges`
 * unconditionally — an absent list is a cleared layout, not an untouched one. `viewport` is the
 * exception: it is genuinely optional in the contract, and `undefined` drops out of the JSON body.
 */
@Injectable({ providedIn: 'root' })
export class DiagramDefinitionMapper implements BaseEntityMapper<DiagramDefinition> {
  fromDto(dto: unknown): DiagramDefinition {
    const source = dto as DiagramDefinitionDto;
    const entityName = source.entityName ?? source.id ?? '';
    return new DiagramDefinition({
      id: entityName,
      entityName,
      nodes: (source.nodes ?? []).map(toNodeLayout),
      edges: (source.edges ?? []).map(toEdgeLayout),
      viewport: source.viewport ? toViewport(source.viewport) : undefined,
      orgKey: source.orgKey,
      version: source.version,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    });
  }

  toDto(entity: DiagramDefinition): DiagramDefinitionDto {
    // Listed field by field rather than spread, so nothing the canvas may hang on the layout later can
    // leak into the payload unnoticed.
    const entityName = entity.entityName || (entity.id ?? '');
    return {
      id: entityName,
      entityName,
      nodes: (entity.nodes ?? []).map(fromNodeLayout),
      edges: (entity.edges ?? []).map(fromEdgeLayout),
      viewport: entity.viewport ? fromViewport(entity.viewport) : undefined,
      orgKey: entity.orgKey,
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}

// region private helper functions
function toPoint(dto: PointDto): Point {
  return new Point({ x: dto.x, y: dto.y });
}

function fromPoint(point: Point): PointDto {
  return { x: point.x, y: point.y };
}

function toNodeLayout(dto: NodeLayoutDto): NodeLayout {
  return new NodeLayout({
    stateKey: dto.stateKey,
    position: dto.position ? toPoint(dto.position) : undefined,
    // An absent size is the default — the node is sized by its content — so it stays absent rather
    // than becoming a 0x0 box the canvas would collapse.
    size: dto.size ? new NodeSize({ width: dto.size.width, height: dto.size.height }) : undefined,
  });
}

function fromNodeLayout(node: NodeLayout): NodeLayoutDto {
  return {
    stateKey: node.stateKey,
    position: fromPoint(node.position),
    size: node.size ? { width: node.size.width, height: node.size.height } : undefined,
  };
}

/**
 * `null` is normalized to `undefined` on the way in: the contract marks the port and routing fields
 * `nullable`, and a `null` port would be handed to ng-diagram as an anchor request rather than being
 * understood as "anchor this edge automatically".
 */
function toEdgeLayout(dto: EdgeLayoutDto): EdgeLayout {
  return new EdgeLayout({
    transitionKey: dto.transitionKey,
    points: (dto.points ?? []).map(toPoint),
    sourcePort: dto.sourcePort ?? undefined,
    targetPort: dto.targetPort ?? undefined,
    routing: dto.routing ?? undefined,
  });
}

function fromEdgeLayout(edge: EdgeLayout): EdgeLayoutDto {
  return {
    transitionKey: edge.transitionKey,
    points: (edge.points ?? []).map(fromPoint),
    sourcePort: edge.sourcePort,
    targetPort: edge.targetPort,
    routing: edge.routing,
  };
}

function toViewport(dto: DiagramViewportDto): DiagramViewport {
  return new DiagramViewport({ x: dto.x, y: dto.y, scale: dto.scale });
}

function fromViewport(viewport: DiagramViewport): DiagramViewportDto {
  return { x: viewport.x, y: viewport.y, scale: viewport.scale };
}
// endregion
