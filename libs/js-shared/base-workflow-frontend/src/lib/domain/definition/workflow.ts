import { BaseEntity } from '@processpuzzle/base-entity';
import { PropertyMap } from '../property-map';

/**
 * Frontend model of `Workflow` — a SPEM-inspired workflow, and the aggregate root of the
 * definition layer. Addressed by the author-chosen `id` — `software-delivery`,
 * `order-fulfillment-workflow` — which is unique per tenant and doubles as the record's identity for
 * the generic screens, so nothing has to be mirrored the way base-state's
 * `StateMachineDefinition.id` mirrors its `entityName`.
 *
 * A workflow **composes catalog entities it does not own**. Its roles, artifacts and tools point at
 * `/roles`, `/artifacts` and `/tools`, each authored once per tenant and shared across workflows — so
 * the same `Order Clerk` or `Fulfillment Invoice` is one record, not one per workflow that mentions
 * it. What the workflow holds is not the id, though: it is a `*Use` row wrapping the id. See
 * {@link RoleUse}.
 *
 * `PUT /workflows/{workflowId}` is a full replacement, so every list travels on every save — an absent
 * one is an emptied workflow, not an untouched one. That is also why {@link startCondition} is
 * flattened onto this entity rather than left off the form: a field the model does not carry is a
 * field the next save deletes.
 *
 * Field names are the contract's throughout, including `extends`. It is a reserved *word* in
 * JavaScript but a perfectly ordinary property *name*, so the payload keeps the schema's spelling
 * rather than the backend column's (`extendsWorkflowId`), and no caller has to translate.
 */

/** How a task's `dependsOn` set is satisfied: every named task, or the first of them. */
export enum JoinType {
  ALL = 'ALL',
  ANY = 'ANY',
}

/** How an instance of a workflow comes into being. Selects which start-condition fields carry meaning. */
export enum WorkflowStartConditionType {
  INPUT_ARTIFACT = 'INPUT_ARTIFACT',
  TRIGGERING_EVENT = 'TRIGGERING_EVENT',
  ROLE_DEFINITION = 'ROLE_DEFINITION',
  TIME_BASED_PRECONDITION = 'TIME_BASED_PRECONDITION',
}

/**
 * A `Workflow Role Definition` taking part in this workflow.
 *
 * A wrapper around one id, and that is the contract's shape rather than an oversight: `RoleUse` is the
 * extension point for whatever turns out to be true of a shared role only *here*. Until something is,
 * the row shows one field.
 *
 * A class rather than an interface, because it is an embedded entity of its own:
 * `EmbeddedEntityFacade` mints the blank row an `Add` opens the child's form on, and that needs a
 * constructor. It stays plain data — the rows of a loaded workflow are the parsed JSON, never
 * instances of this class.
 *
 * It declares but never assigns `id`: the contract gives a `*Use` no key of its own, and the
 * definition id it wraps is what identifies it within the workflow. Same arrangement as
 * {@link WorkflowTaskAssignment}.
 */
export class RoleUse implements BaseEntity {
  declare readonly id?: string;

  /** Id of a `Workflow Role Definition` of this organization. */
  roleDefinitionId: string;

  constructor(init: Partial<RoleUse> = {}) {
    this.roleDefinitionId = init.roleDefinitionId ?? '';
  }
}

/** An `Artifact Definition` this workflow produces or consumes. See {@link RoleUse}. */
export class ArtifactUse implements BaseEntity {
  declare readonly id?: string;

  /** Id of an `Artifact Definition` of this organization. */
  artifactDefinitionId: string;

  constructor(init: Partial<ArtifactUse> = {}) {
    this.artifactDefinitionId = init.artifactDefinitionId ?? '';
  }
}

/** A `Tool Definition` this workflow's task steps may invoke. See {@link RoleUse}. */
export class ToolUse implements BaseEntity {
  declare readonly id?: string;

  /** Id of a `Tool Definition` of this organization. */
  toolDefinitionId: string;

  constructor(init: Partial<ToolUse> = {}) {
    this.toolDefinitionId = init.toolDefinitionId ?? '';
  }
}

/**
 * One artifact an `INPUT_ARTIFACT` start condition waits for, and optionally the state it has to be
 * in.
 *
 * `state` is named by the artifact's base-state machine; base-workflow records the name and never
 * resolves it, so this is a plain string rather than a reference. Absent means any state will do.
 */
export class RequiredStartArtifact implements BaseEntity {
  declare readonly id?: string;

  artifactDefinitionId: string;
  state?: string;

  constructor(init: Partial<RequiredStartArtifact> = {}) {
    this.artifactDefinitionId = init.artifactDefinitionId ?? '';
    this.state = init.state;
  }
}

/**
 * One task's place in one workflow: which of the task's `performedByRoles` performs it here, what has
 * to finish first, and whether it may run beside its siblings.
 *
 * This is the contract's `TaskUse`. The name here is the older one and has stayed, because it is what
 * the route segment, the transloco keys and the e2e fixtures spell.
 *
 * A class rather than an interface, for the same reason as {@link RoleUse}.
 *
 * It declares but never assigns `id`: the contract gives an assignment no key of its own, and
 * `taskDefinitionId` is what identifies it within the workflow.
 */
export class WorkflowTaskAssignment implements BaseEntity {
  declare readonly id?: string;

  /** Id of a `Task Definition` of this organization. A task appears at most once per workflow. */
  taskDefinitionId: string;
  /**
   * `RoleDefinition.id` of the role that performs the task *here*. Has to be one of this workflow's
   * {@link Workflow.roles} and one of the task's `performedByRoles` — the task says who is
   * able to perform it, the workflow says who does.
   */
  performedBy: string;
  /** Task definition ids of assignments in this same workflow that must be COMPLETED first. */
  dependsOn?: string[];
  /** Whether every task named in {@link dependsOn} has to finish, or only the first of them. */
  joinType?: JoinType;
  /** May run concurrently with siblings sharing the same {@link dependsOn}. */
  parallel: boolean;
  /** Replaces the parent workflow's assignment of the same task rather than adding to it. */
  override: boolean;

  constructor(init: Partial<WorkflowTaskAssignment> = {}) {
    this.taskDefinitionId = init.taskDefinitionId ?? '';
    this.performedBy = init.performedBy ?? '';
    this.dependsOn = init.dependsOn;
    this.joinType = init.joinType;
    this.parallel = init.parallel ?? false;
    this.override = init.override ?? false;
  }
}

export class Workflow implements BaseEntity {
  id: string;
  name: string;
  description?: string;
  /** Id of a parent workflow to inherit roles, artifacts, tools and task assignments from. */
  extends?: string;
  /** The `Workflow Role Definition`s taking part in this workflow. */
  roles: RoleUse[];
  /** The `Artifact Definition`s this workflow produces or consumes. */
  artifacts: ArtifactUse[];
  /** The `Tool Definition`s this workflow's steps may invoke. */
  tools: ToolUse[];
  tasks: WorkflowTaskAssignment[];
  // region start condition — flattened from the contract's nested `startCondition`
  /**
   * Flattened rather than nested, following the `auth` fields of `Tool Definition`: the generic form
   * builds one control per attribute of one entity, so a nested object would need an embedded entity
   * for a thing that is not a list. `WorkflowMapper` re-nests all seven on save.
   *
   * `startType` decides which of the rest carry meaning; the others are ignored by the backend rather
   * than rejected, so the form shows them all and the author fills in the ones their choice needs.
   * A workflow with no `startType` can only be started explicitly through `/instances`.
   */
  startType?: WorkflowStartConditionType;
  /** INPUT_ARTIFACT — the artifacts, and optionally the states, that must be present. */
  requiredArtifacts: RequiredStartArtifact[];
  /** TRIGGERING_EVENT — the event that starts the workflow. */
  eventType?: string;
  /** TRIGGERING_EVENT — maps the event payload into the new instance's context, by JSONPath. */
  payloadMapping?: PropertyMap;
  /** ROLE_DEFINITION — role definition ids allowed to start the workflow by hand. */
  authorizedRoles: string[];
  /** TIME_BASED_PRECONDITION — the milestone whose arrival is the trigger. */
  milestoneRef?: string;
  /** TIME_BASED_PRECONDITION — PPCL guard that must hold when the milestone arrives. */
  preconditionExpression?: string;
  // endregion
  // region server-assigned
  /** Number of ACTIVE instances; computed per row by the list endpoint, never sent on write. */
  activeInstances: number | undefined;
  version: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  // endregion

  constructor(init: Partial<Workflow> = {}) {
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
    this.startType = init.startType;
    this.requiredArtifacts = init.requiredArtifacts ?? [];
    this.eventType = init.eventType;
    this.payloadMapping = init.payloadMapping;
    this.authorizedRoles = init.authorizedRoles ?? [];
    this.milestoneRef = init.milestoneRef;
    this.preconditionExpression = init.preconditionExpression;
    this.activeInstances = init.activeInstances;
    this.version = init.version;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
