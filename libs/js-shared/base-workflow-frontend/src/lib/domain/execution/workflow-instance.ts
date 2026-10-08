import { BaseEntity } from '@processpuzzle/base-entity';
import { ArtifactType } from '../definition/artifact-definition';
import { EventDirection } from '../definition/workflow';
import { PropertyMap } from '../property-map';

/**
 * Frontend model of the execution layer of `base-workflow-api.yaml`: a running `WorkflowInstance` with
 * its task and artifact instances, and the per-step outcomes a task records.
 *
 * The whole layer is **read-only**, and that is a property of the contract rather than a choice made
 * here: there is no `PUT`. An instance is born from `POST /instances` (`startWorkflowInstance`), it
 * ends through `DELETE /instances/{id}` (`cancelWorkflowInstance`), and a task moves through
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

/** Mirrors the contract's `WorkflowInstanceStatus`. */
/** Where an intermediate event of a run stands: not reached, waiting, occurred, thrown, or withdrawn. */
export enum EventInstanceStatus {
  PENDING = 'PENDING',
  WAITING = 'WAITING',
  OCCURRED = 'OCCURRED',
  THROWN = 'THROWN',
  CANCELLED = 'CANCELLED',
}

export enum WorkflowInstanceStatus {
  ACTIVE = 'ACTIVE',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
  SUSPENDED = 'SUSPENDED',
}

/** One task of a running workflow: where it stands, who holds it, and what its steps produced. */
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
 * One artifact of a running workflow. Both of its interesting fields are *references into other
 * features*: `entityId` addresses the data in base-entity, `stateMachineInstanceId` the machine in
 * base-state. `currentState` is a cached copy base-workflow refreshes when it sees
 * `EntityStateChangedEvent`, offered so a workflow overview can be rendered without querying
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

/** A running workflow: the aggregate root of the execution layer, addressed by its server-minted UUID. */
/**
 * The run-time state of one intermediate event of an instance — the contract's `EventInstance`. Read-only:
 * the engine throws it, or delivers the catalog event a catch waits for.
 */
export class EventInstance implements BaseEntity {
  id: string;
  /** `EventUse.id` of the workflow this row runs. */
  eventUseId: string;
  eventDefinitionId: string;
  name?: string;
  direction: EventDirection | undefined;
  status: EventInstanceStatus | undefined;
  /** THROW: the value sent. CATCH: the value waited for. */
  correlationValue?: string;
  waitingSince?: string;
  occurredAt?: string;
  payload?: PropertyMap;
  /** CATCH: what the payload mapping added to the context. */
  contextContribution?: PropertyMap;

  constructor(init: Partial<EventInstance> = {}) {
    this.id = init.id ?? '';
    this.eventUseId = init.eventUseId ?? '';
    this.eventDefinitionId = init.eventDefinitionId ?? '';
    this.name = init.name;
    this.direction = init.direction;
    this.status = init.status;
    this.correlationValue = init.correlationValue;
    this.waitingSince = init.waitingSince;
    this.occurredAt = init.occurredAt;
    this.payload = init.payload;
    this.contextContribution = init.contextContribution;
  }
}

export class WorkflowInstance implements BaseEntity {
  id: string;
  /** Server-assigned, sequential per organization: the instance's human-facing identity. */
  instanceNumber?: number;
  /**
   * What the form and status bar call the run — `Order Fulfillment Workflow #3`: the definition alone
   * would name every run of it the same. Derived here, never sent.
   */
  title: string;
  /** {@link Workflow.id} this instance runs. */
  workflowId: string;
  /** `StartEvent.id` of the event this instance was started through; absent for an explicit start. */
  startEventId?: string;
  /** Denormalized name of the definition, so a list needs no second read. */
  workflowName?: string;
  status: WorkflowInstanceStatus | undefined;
  /** The base-entity instance this workflow runs against, when it was started for one. */
  entityId?: string;
  /** The base-entity definition code of {@link entityId}'s object, e.g. `order`. Server-set. */
  entityType?: string;
  /** {@link entityId}'s object by name — its order number — resolved per response; the id when nameless. */
  entityLabel?: string;
  startedAt?: string;
  completedAt?: string;
  /** Context variables, updated by the output mappings of every tool step that has run. */
  context?: PropertyMap;
  tasks: TaskInstance[];
  artifacts: ArtifactInstance[];
  /** The run-time state of the workflow's intermediate events. */
  events: EventInstance[];

  constructor(init: Partial<WorkflowInstance> = {}) {
    this.id = init.id ?? '';
    this.instanceNumber = init.instanceNumber;
    this.workflowId = init.workflowId ?? '';
    this.startEventId = init.startEventId;
    this.workflowName = init.workflowName;
    this.status = init.status;
    this.entityId = init.entityId;
    this.entityType = init.entityType;
    this.entityLabel = init.entityLabel;
    this.startedAt = init.startedAt;
    this.completedAt = init.completedAt;
    this.context = init.context;
    this.tasks = init.tasks ?? [];
    this.artifacts = init.artifacts ?? [];
    this.events = init.events ?? [];
    this.title = [this.workflowName, this.instanceNumber === undefined ? undefined : `#${this.instanceNumber}`].filter(Boolean).join(' ');
  }
}
