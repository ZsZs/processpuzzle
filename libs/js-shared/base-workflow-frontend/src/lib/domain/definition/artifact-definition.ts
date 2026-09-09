import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Frontend model of `ArtifactDefinition` — something a workflow produces or consumes whose
 * *lifecycle* is worth tracking, authored once per tenant under `/artifacts`.
 *
 * The tenant's catalog of *everything a task may touch*, and the reason a task's `inputs` and
 * `outputs` are plain ids: {@link artifactType} already says whether the thing is an entity, a
 * document or a widget, so there is nothing left for a per-reference type to add. An artifact may also
 * name a base-state machine, which is what makes its lifecycle trackable.
 */

/**
 * Mirrors the contract's `ArtifactType`: SPEM's kinds of work product, plus `ENTITY` for one whose data
 * is a base-entity instance. Names the kind, not the schema — every value here describes an artifact
 * definition, and this field says which kind of one it is. {@link ArtifactDefinition.artifactTypeId}
 * then names the concrete document, entity or widget.
 *
 * These are the contract's three values. The enum carried SPEM's older `ARTIFACT` / `DELIVERABLE` /
 * `OUTCOME` beside `ENTITY` until this revision, which left the form offering three values the backend
 * rejects and no way at all to author the `DOCUMENT` and `WIDGET` the seed data uses.
 */
export enum ArtifactType {
  DOCUMENT = 'DOCUMENT',
  ENTITY = 'ENTITY',
  WIDGET = 'WIDGET',
}

export class ArtifactDefinition implements BaseEntity {
  id: string;
  name: string;
  description?: string;
  artifactType: ArtifactType | undefined;
  /**
   * Id of the concrete thing this artifact *is*: a base-entity entity name when
   * {@link artifactType} is `ENTITY`, a base-document document name when `DOCUMENT`, a widget name
   * when `WIDGET`.
   */
  artifactTypeId?: string;
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
    this.artifactType = init.artifactType;
    this.artifactTypeId = init.artifactTypeId;
    this.stateMachineId = init.stateMachineId;
    this.version = init.version;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
