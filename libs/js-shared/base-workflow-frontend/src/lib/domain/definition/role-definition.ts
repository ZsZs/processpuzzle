import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Frontend model of `RoleDefinition` — someone who performs tasks, authored once per tenant under
 * `/roles` and referenced by every workflow that needs it.
 *
 * A catalog aggregate rather than a child of a workflow, exactly as the contract has it: the same
 * `Order Clerk` takes part in order fulfillment and in claim handling, and describing them twice
 * would be two records to keep in step. A workflow names the ids it involves; a task names the ids
 * *able* to perform it.
 *
 * `entityRoleId` is the link to base-entity's own role registry: when set, base-workflow refuses to
 * assign a task to a user who does not hold that role.
 */
export class RoleDefinition implements BaseEntity {
  id: string;
  name: string;
  description?: string;
  /**
   * Ids of the `Artifact Definition`s this role owns the outcome of. Ownership, not access: what a
   * task reads or writes is stated on the task.
   */
  responsibleFor: string[];
  /** Id of the corresponding role definition in base-entity; optional by contract. */
  entityRoleId?: string;
  // region server-assigned
  version: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  // endregion

  constructor(init: Partial<RoleDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.description = init.description;
    // An empty array rather than undefined, so the `RELATED_ENTITIES` control has a list to add the
    // first pick to.
    this.responsibleFor = init.responsibleFor ?? [];
    this.entityRoleId = init.entityRoleId;
    this.version = init.version;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
