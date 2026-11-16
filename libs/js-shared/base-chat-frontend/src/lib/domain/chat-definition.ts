import type { ReleaseState } from '@processpuzzle/util';
import { BaseEntity } from '@processpuzzle/base-entity';

/** What a chat is about; `ENTITY` is the only kind there is. */
export enum ChatSubjectKind {
  ENTITY = 'ENTITY',
}

/** One chat per subject, or any number of titled topics. */
export enum ChatMultiplicity {
  SINGLE = 'SINGLE',
  MANY = 'MANY',
}

/**
 * When the conversation comes into being, and whether the subject's state limits writing.
 *
 * - `AUTO_CREATE` — together with its subject.
 * - `ON_DEMAND` — the first time somebody with a create role opens it.
 * - `STATE_BOUND` — on demand, and writable only while the subject is in one of {@link ChatDefinition.writableStates}.
 */
export enum ChatLifecycleKind {
  AUTO_CREATE = 'AUTO_CREATE',
  ON_DEMAND = 'ON_DEMAND',
  STATE_BOUND = 'STATE_BOUND',
}

/**
 * One chat definition — the contract's `ChatDefinition`. Its author-chosen `key` is held as {@link id}, because
 * that is what the generic REST service addresses a record by; the mapper translates.
 */
export class ChatDefinition implements BaseEntity {
  /** The definition's key, unique within the organization, e.g. `order-discussion`. */
  id: string;
  name: string;
  description?: string;
  subjectType: ChatSubjectKind;
  /** The entity definition code whose records get the chat, e.g. `order`. */
  subjectDefinition: string;
  multiplicity: ChatMultiplicity;
  lifecycle: ChatLifecycleKind;
  /** `STATE_BOUND` only — the subject states in which the conversation accepts messages. */
  writableStates: string[];
  readRoles: string[];
  writeRoles: string[];
  createRoles: string[];
  includeSubjectOwner: boolean;
  retentionDays?: number;
  /** Optimistic lock; optional on PUT. */
  version?: number;
  /** Where this draft stands against the release; reported by the backend, never sent back. */
  releaseState?: ReleaseState;

  constructor(init: Partial<ChatDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.description = init.description;
    this.subjectType = init.subjectType ?? ChatSubjectKind.ENTITY;
    this.subjectDefinition = init.subjectDefinition ?? '';
    this.multiplicity = init.multiplicity ?? ChatMultiplicity.SINGLE;
    this.lifecycle = init.lifecycle ?? ChatLifecycleKind.ON_DEMAND;
    this.writableStates = init.writableStates ?? [];
    this.readRoles = init.readRoles ?? [];
    this.writeRoles = init.writeRoles ?? [];
    this.createRoles = init.createRoles ?? [];
    this.includeSubjectOwner = init.includeSubjectOwner ?? false;
    this.retentionDays = init.retentionDays;
    this.version = init.version;
    this.releaseState = init.releaseState;
  }
}
