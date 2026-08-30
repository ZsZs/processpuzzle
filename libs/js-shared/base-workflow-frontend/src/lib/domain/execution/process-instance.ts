import { BaseEntity } from '@processpuzzle/base-entity';
import { ArtifactType } from '../definition/artifact-definition';
import { PropertyMap } from '../property-map';

/**
 * Frontend model of the execution layer of `base-workflow-api.yaml`: a running `ProcessInstance` with
 * its task and artifact instances, and the per-step outcomes a task records.
 *
 * The whole layer is **read-only**, and that is a property of the contract rather than a choice made
 * here: there is no `PUT`. An instance is born from `POST /instances` (`startProcessInstance`), it
 * ends through `DELETE /instances/{id}` (`cancelProcessInstance`), and a task moves through
 * `/assign`, `/complete` and `/skip` — verbs, not field edits. The descriptors say so by declaring
 * `isAbstract`, which is what disables New, Edit, Delete and Save on the generated screens; the
 * models are still full and mutable so a future action surface can build a payload out of them.
 *
 * {@link PropertyMap} and {@link ArtifactType} are shared rather than repeated: the contract's
 * `context` is the same open map a step's mappings write into, and an artifact instance's `type` is
 * its definition's.
 */

/** The outcome of one step of a task — recorded only for steps that invoked a tool. */
export class StepResult implements BaseEntity {
  /** Declared, never assigned: `stepId` identifies a result. The contract gives it no `id`. */
  declare readonly id?: string;

  /** {@link StepDefinition.id} this result belongs to. */
  stepId: string;
  completedAt?: string;
  /** Raw response body of the tool call, if this step invoked one. */
  toolResponse?: PropertyMap;
  error?: string;

  constructor(init: Partial<StepResult> = {}) {
    this.stepId = init.stepId ?? '';
    this.completedAt = init.completedAt;
    this.toolResponse = init.toolResponse;
    this.error = init.error;
  }
}

/** Mirrors the contract's `TaskInstanceStatus`. */
export enum TaskInstanceStatus {
  PENDING = 'PENDING',
  ACTIVE = 'ACTIVE',
  COMPLETED = 'COMPLETED',
  SKIPPED = 'SKIPPED',
  BLOCKED = 'BLOCKED',
}

/** Mirrors the contract's `ProcessInstanceStatus`. */
export enum ProcessInstanceStatus {
  ACTIVE = 'ACTIVE',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
  SUSPENDED = 'SUSPENDED',
}

/** One task of a running process: where it stands, who holds it, and what its steps produced. */
export class TaskInstance implements BaseEntity {
  id: string;
  /** {@link TaskDefinition.id} this instance was created from. */
  taskDefinitionId: string;
  name: string;
  status: TaskInstanceStatus | undefined;
  /** User id, from base-entity. */
  assignedTo?: string;
  /** Set while BLOCKED: the detail of the precondition evaluation that prevented activation. */
  blockedReason?: string;
  activatedAt?: string;
  completedAt?: string;
  skippedAt?: string;
  stepResults: StepResult[];

  constructor(init: Partial<TaskInstance> = {}) {
    this.id = init.id ?? '';
    this.taskDefinitionId = init.taskDefinitionId ?? '';
    this.name = init.name ?? '';
    this.status = init.status;
    this.assignedTo = init.assignedTo;
    this.blockedReason = init.blockedReason;
    this.activatedAt = init.activatedAt;
    this.completedAt = init.completedAt;
    this.skippedAt = init.skippedAt;
    this.stepResults = init.stepResults ?? [];
  }
}

/**
 * One artifact of a running process. Both of its interesting fields are *references into other
 * features*: `entityId` addresses the data in base-entity, `stateMachineInstanceId` the machine in
 * base-state. `currentState` is a cached copy base-workflow refreshes when it sees
 * `EntityStateChangedEvent`, offered so a process overview can be rendered without querying
 * base-state — base-state remains the authority.
 */
export class ArtifactInstance implements BaseEntity {
  id: string;
  /** {@link ArtifactDefinition.id} this instance was created from. */
  artifactDefinitionId: string;
  name: string;
  type: ArtifactType | undefined;
  entityId?: string;
  stateMachineInstanceId?: string;
  currentState?: string;
  updatedAt?: string;

  constructor(init: Partial<ArtifactInstance> = {}) {
    this.id = init.id ?? '';
    this.artifactDefinitionId = init.artifactDefinitionId ?? '';
    this.name = init.name ?? '';
    this.type = init.type;
    this.entityId = init.entityId;
    this.stateMachineInstanceId = init.stateMachineInstanceId;
    this.currentState = init.currentState;
    this.updatedAt = init.updatedAt;
  }
}

/** A running process: the aggregate root of the execution layer, addressed by its server-minted UUID. */
export class ProcessInstance implements BaseEntity {
  id: string;
  /** {@link ProcessDefinition.id} this instance runs. */
  processDefinitionId: string;
  /** Denormalized name of the definition, so a list needs no second read. */
  processDefinitionName?: string;
  status: ProcessInstanceStatus | undefined;
  /** The base-entity instance this process runs against, when it was started for one. */
  entityId?: string;
  startedAt?: string;
  completedAt?: string;
  /** Context variables, updated by the output mappings of every tool step that has run. */
  context?: PropertyMap;
  tasks: TaskInstance[];
  artifacts: ArtifactInstance[];

  constructor(init: Partial<ProcessInstance> = {}) {
    this.id = init.id ?? '';
    this.processDefinitionId = init.processDefinitionId ?? '';
    this.processDefinitionName = init.processDefinitionName;
    this.status = init.status;
    this.entityId = init.entityId;
    this.startedAt = init.startedAt;
    this.completedAt = init.completedAt;
    this.context = init.context;
    this.tasks = init.tasks ?? [];
    this.artifacts = init.artifacts ?? [];
  }
}
