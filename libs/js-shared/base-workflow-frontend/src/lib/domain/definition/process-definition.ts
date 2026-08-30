import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Frontend model of `ProcessDefinition` — a SPEM-inspired process, and the aggregate root of the
 * definition layer. Addressed by the author-chosen `id` — `software-delivery`,
 * `order-fulfillment-workflow` — which is unique per tenant and doubles as the record's identity for
 * the generic screens, so nothing has to be mirrored the way base-state's
 * `StateMachineDefinition.id` mirrors its `entityName`.
 *
 * A process **composes catalog entities it does not own**. Its roles, artifacts and tools are id
 * lists pointing at `/roles`, `/artifacts` and `/tools`, each authored once per tenant and shared
 * across processes — so the same `Order Clerk` or `Fulfillment Invoice` is one record, not one per
 * process that mentions it. Only {@link ProcessTaskAssignment} is nested, because an assignment has
 * no meaning outside the process making it.
 *
 * `PUT /processes/{processId}` is a full replacement, so every list travels on every save — an absent
 * one is an emptied process, not an untouched one. `ProcessDefinitionMapper` is what keeps the id
 * lists flat in both directions: the `RELATED_ENTITIES` control writes whole entities into its
 * control on selection, while the contract wants strings.
 *
 * Field names are the contract's throughout, including `extends`. It is a reserved *word* in
 * JavaScript but a perfectly ordinary property *name*, so the payload keeps the schema's spelling
 * rather than the backend column's (`extendsProcessId`), and no caller has to translate.
 */

/**
 * One task's place in one process: which of the task's `performedByRoles` performs it here, what has
 * to finish first, and whether it may run beside its siblings.
 *
 * A class rather than an interface, because it is an embedded entity of its own:
 * `EmbeddedEntityFacade` mints the blank row an `Add` opens the child's form on, and that needs a
 * constructor. It stays plain data — the rows of a loaded process are the parsed JSON, never
 * instances of this class.
 *
 * It declares but never assigns `id`: the contract gives an assignment no key of its own, and
 * `taskDefinitionId` is what identifies it within the process. Same arrangement as
 * {@link TaskIOReference}, whose identity is its `refId`.
 */
export class ProcessTaskAssignment implements BaseEntity {
  declare readonly id?: string;

  /** Id of a `Task Definition` of this organization. A task appears at most once per process. */
  taskDefinitionId: string;
  /**
   * `RoleDefinition.id` of the role that performs the task *here*. Has to be one of this process's
   * {@link ProcessDefinition.roles} and one of the task's `performedByRoles` — the task says who is
   * able to perform it, the process says who does.
   */
  performedBy: string;
  /** Task definition ids of assignments in this same process that must be COMPLETED first. */
  dependsOn?: string[];
  /** May run concurrently with siblings sharing the same {@link dependsOn}. */
  parallel: boolean;
  /** Replaces the parent process's assignment of the same task rather than adding to it. */
  override: boolean;

  constructor(init: Partial<ProcessTaskAssignment> = {}) {
    this.taskDefinitionId = init.taskDefinitionId ?? '';
    this.performedBy = init.performedBy ?? '';
    this.dependsOn = init.dependsOn;
    this.parallel = init.parallel ?? false;
    this.override = init.override ?? false;
  }
}

export class ProcessDefinition implements BaseEntity {
  id: string;
  name: string;
  description?: string;
  /** Id of a parent process to inherit roles, artifacts and task assignments from. */
  extends?: string;
  /** Ids of the `Workflow Role Definition`s taking part in this process. */
  roles: string[];
  /** Ids of the `Artifact Definition`s this process produces or consumes. */
  artifacts: string[];
  /** Ids of the `Tool Definition`s this process's steps may invoke. */
  tools: string[];
  tasks: ProcessTaskAssignment[];
  // region server-assigned
  /** Number of ACTIVE instances; computed per row by the list endpoint, never sent on write. */
  activeInstances: number | undefined;
  version: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  // endregion

  constructor(init: Partial<ProcessDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.description = init.description;
    this.extends = init.extends;
    // Empty arrays rather than undefined, so every reference and assignment control has a list to add
    // its first entry to.
    this.roles = init.roles ?? [];
    this.artifacts = init.artifacts ?? [];
    this.tools = init.tools ?? [];
    this.tasks = init.tasks ?? [];
    this.activeInstances = init.activeInstances;
    this.version = init.version;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
