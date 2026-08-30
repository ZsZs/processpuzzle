import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Frontend model of `ArtifactDefinition` — something a process produces or consumes whose
 * *lifecycle* is worth tracking, authored once per tenant under `/artifacts`.
 *
 * That lifecycle is what separates it from a plain `TaskIOReference`: an artifact may name a
 * base-state machine, a reference only grants read or write access. It is also why an
 * `ARTIFACT`-typed reference has to name one of these — see {@link ReferenceType}.
 */

/**
 * Mirrors the contract's `ArtifactType`: SPEM's three kinds of work product, plus `ENTITY` for one
 * whose data is a base-entity instance. `ARTIFACT` names the kind, not the schema — every value here
 * describes an artifact definition, and this field says which kind of one it is.
 */
export enum ArtifactType {
  ARTIFACT = 'ARTIFACT',
  DELIVERABLE = 'DELIVERABLE',
  OUTCOME = 'OUTCOME',
  ENTITY = 'ENTITY',
}

export class ArtifactDefinition implements BaseEntity {
  id: string;
  name: string;
  description?: string;
  type: ArtifactType | undefined;
  /** Id of the base-entity entity type backing this artifact's data. */
  entityTypeId?: string;
  /** Id of the base-state machine governing this artifact's lifecycle. */
  stateMachineId?: string;
  // region server-assigned
  version: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  // endregion

  constructor(init: Partial<ArtifactDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.description = init.description;
    this.type = init.type;
    this.entityTypeId = init.entityTypeId;
    this.stateMachineId = init.stateMachineId;
    this.version = init.version;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
