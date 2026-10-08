import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * How an event comes into being.
 *
 * - `SYSTEM` — published by the platform itself when an entity object of {@link EventDefinition.subjectType}
 *   undergoes {@link EventDefinition.action}. The only kind that carries a subject.
 * - `MESSAGE` — addressed to one receiver, published by a caller through the API.
 * - `SIGNAL` — broadcast to whoever listens, published by a caller through the API.
 */
export enum EventKind {
  SYSTEM = 'SYSTEM',
  MESSAGE = 'MESSAGE',
  SIGNAL = 'SIGNAL',
}

/** What happened to the subject of a `SYSTEM` event. */
export enum EventAction {
  CREATED = 'CREATED',
  UPDATED = 'UPDATED',
  DELETED = 'DELETED',
  STATE_CHANGED = 'STATE_CHANGED',
}

/**
 * One entry of the platform's event catalog — the contract's `EventDefinition`.
 *
 * The catalog is what other features name an event by: a workflow's `TRIGGERING_EVENT` start event stores
 * an event definition's {@link id} (`OrderCreatedEvent`) in its `eventType`, and the backend matches every
 * published event against the catalog by that id. The id is therefore author-chosen and stable, not a
 * generated key.
 */
export class EventDefinition implements BaseEntity {
  /** Author-chosen, unique within the organization, e.g. `OrderCreatedEvent`. */
  id: string;
  name: string;
  description?: string;
  kind: EventKind;
  /** `SYSTEM` only — the entity definition code whose objects raise the event, e.g. `order`. */
  subjectType?: string;
  /** `SYSTEM` only — what happened to the subject. */
  action?: EventAction;
  /** Only with {@link EventAction.STATE_CHANGED} — the state the subject entered, e.g. `CONFIRMED`. */
  state?: string;
  /** Optimistic lock; optional on PUT — omitted, the write is unconditional. */
  version?: number;

  constructor(init: Partial<EventDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.description = init.description;
    this.kind = init.kind ?? EventKind.SYSTEM;
    this.subjectType = init.subjectType;
    this.action = init.action;
    this.state = init.state;
    this.version = init.version;
  }
}
