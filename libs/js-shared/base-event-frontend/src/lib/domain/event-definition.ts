import type { ReleaseState } from '@processpuzzle/util';
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
  /** A document was signed for the subject — base-document's fact, about the object signed for. */
  SIGNED = 'SIGNED',
}

/** The type of a value an event carries — the contract's `EventParameterType`. */
export enum EventParameterType {
  STRING = 'STRING',
  NUMBER = 'NUMBER',
  BOOLEAN = 'BOOLEAN',
  DATE = 'DATE',
  /** A reference to one record of {@link EventParameter.entityType} — its id. */
  ENTITY_REF = 'ENTITY_REF',
}

/**
 * One value an occurrence of the event carries — the contract's `EventParameter`.
 *
 * A `SYSTEM` event fills a declared parameter from the subject's attribute of the same name, and always
 * carries the implicit `ENTITY_REF` `subject`, which is not declared. A `MESSAGE` or `SIGNAL` thrown by a
 * workflow fills it from the thrower's context, by name.
 *
 * A class rather than an interface, because it is an embedded entity of its own: `EmbeddedEntityFacade`
 * mints the blank row an `Add` opens the child's form on. It has no `id` — its {@link name}, unique within
 * the definition, identifies it, so the referencing attribute sets `referenceIdField`.
 */
export class EventParameter implements BaseEntity {
  declare readonly id?: string;

  /** An identifier, unique within the definition, e.g. `invoiceNumber`; `subject` is reserved. */
  name: string;
  type: EventParameterType;
  /** `ENTITY_REF` only — the base-entity definition code, or `document`. */
  entityType?: string;
  required: boolean;
  description?: string;

  constructor(init: Partial<EventParameter> = {}) {
    this.name = init.name ?? '';
    this.type = init.type ?? EventParameterType.STRING;
    this.entityType = init.entityType;
    this.required = init.required ?? false;
    this.description = init.description;
  }
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
  /** What an occurrence of the event carries; empty for a definition that declares nothing. */
  parameters: EventParameter[];
  /** Optimistic lock; optional on PUT — omitted, the write is unconditional. */
  version?: number;
  /** Where this draft stands against the release; reported by the backend, never sent back. */
  releaseState?: ReleaseState;

  constructor(init: Partial<EventDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.description = init.description;
    this.kind = init.kind ?? EventKind.SYSTEM;
    this.subjectType = init.subjectType;
    this.action = init.action;
    this.state = init.state;
    this.parameters = init.parameters ?? [];
    this.version = init.version;
    this.releaseState = init.releaseState;
  }
}
