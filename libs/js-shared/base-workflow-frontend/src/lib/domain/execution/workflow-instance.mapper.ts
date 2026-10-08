import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { ArtifactType } from '../definition/artifact-definition';
import { EventDirection } from '../definition/workflow';
import { PropertyMap } from '../property-map';
import { ArtifactInstance, EventInstance, EventInstanceStatus, WorkflowInstance, WorkflowInstanceStatus, StepResult, TaskInstance, TaskInstanceStatus } from './workflow-instance';

// region wire shapes
interface StepResultDto {
  stepId?: string;
  completedAt?: string;
  toolResponse?: PropertyMap;
  error?: string;
}

export interface TaskInstanceDto {
  id?: string;
  taskDefinitionId?: string;
  name?: string;
  status?: TaskInstanceStatus;
  assignedTo?: string;
  blockedReason?: string;
  activatedAt?: string;
  completedAt?: string;
  skippedAt?: string;
  cancelledAt?: string | null;
  cancelReason?: string | null;
  stepResults?: StepResultDto[];
}

interface ArtifactInstanceDto {
  id?: string;
  artifactDefinitionId?: string;
  name?: string;
  type?: ArtifactType;
  entityId?: string;
  stateMachineInstanceId?: string;
  currentState?: string;
  updatedAt?: string;
}

interface EventInstanceDto {
  id?: string;
  eventUseId?: string;
  eventDefinitionId?: string | null;
  name?: string | null;
  direction?: EventDirection;
  status?: EventInstanceStatus;
  correlationValue?: string | null;
  waitingSince?: string | null;
  occurredAt?: string | null;
  payload?: PropertyMap | null;
  contextContribution?: PropertyMap | null;
  dueAt?: string | null;
  fireCount?: number;
}

interface WorkflowInstanceDto {
  id?: string;
  instanceNumber?: number;
  workflowId?: string;
  startEventId?: string;
  workflowName?: string;
  status?: WorkflowInstanceStatus;
  entityId?: string;
  entityType?: string;
  entityLabel?: string;
  startedAt?: string;
  completedAt?: string;
  context?: PropertyMap;
  tasks?: TaskInstanceDto[];
  artifacts?: ArtifactInstanceDto[];
  events?: EventInstanceDto[];
}
// endregion

/**
 * Translates between the `WorkflowInstance` DTO of `base-workflow-api.yaml` and the entity the
 * generated screens render.
 *
 * `toDto` exists because `BaseEntityMapper` requires it, not because anything sends it: the contract
 * defines no `PUT /instances/{id}`, so the only writes are `POST /instances` — whose body is a
 * `StartWorkflowRequest`, a different schema entirely — and the three task verbs. It is implemented
 * faithfully rather than left throwing, so the store's optimistic paths and any future action surface
 * have a payload to build on; nothing today reaches the network with it.
 *
 * The nested rows are mapped element by element for the same reason as in the definition mapper: an
 * embedded row is edited as the JSON it arrived as, so a field spelled differently on the wire would
 * silently show blank.
 */
@Injectable({ providedIn: 'root' })
export class WorkflowInstanceMapper implements BaseEntityMapper<WorkflowInstance> {
  fromDto(dto: unknown): WorkflowInstance {
    const source = dto as WorkflowInstanceDto;
    return new WorkflowInstance({
      id: source.id,
      instanceNumber: source.instanceNumber ?? undefined,
      workflowId: source.workflowId,
      startEventId: source.startEventId,
      workflowName: source.workflowName,
      status: source.status,
      entityId: source.entityId,
      entityType: source.entityType,
      entityLabel: source.entityLabel,
      startedAt: source.startedAt,
      completedAt: source.completedAt,
      context: source.context,
      tasks: (source.tasks ?? []).map(toTaskInstance),
      artifacts: (source.artifacts ?? []).map(toArtifactInstance),
      events: (source.events ?? []).map(toEventInstance),
    });
  }

  toDto(entity: WorkflowInstance): WorkflowInstanceDto {
    return {
      id: entity.id,
      instanceNumber: entity.instanceNumber,
      workflowId: entity.workflowId,
      startEventId: entity.startEventId,
      workflowName: entity.workflowName,
      status: entity.status,
      entityId: entity.entityId,
      entityType: entity.entityType,
      entityLabel: entity.entityLabel,
      startedAt: entity.startedAt,
      completedAt: entity.completedAt,
      context: entity.context,
      tasks: (entity.tasks ?? []).map(fromTaskInstance),
      artifacts: (entity.artifacts ?? []).map(fromArtifactInstance),
      events: (entity.events ?? []).map(fromEventInstance),
    };
  }
}

// region row helpers — private but for toTaskInstance, which the task verbs share
function toStepResult(dto: StepResultDto): StepResult {
  return new StepResult({ stepId: dto.stepId, completedAt: dto.completedAt, toolResponse: dto.toolResponse, error: dto.error });
}

function fromStepResult(result: StepResult): StepResultDto {
  return { stepId: result.stepId, completedAt: result.completedAt, toolResponse: result.toolResponse, error: result.error };
}

/**
 * Wire → model for one task instance, exported because the three task verbs answer with exactly this
 * shape: `TaskActionService` maps `/assign`, `/complete` and `/skip` responses through it rather than
 * repeating the field list, which is what keeps a renamed wire field from showing blank on the
 * dashboard while the instance screens still read it correctly.
 */
export function toTaskInstance(dto: TaskInstanceDto): TaskInstance {
  return new TaskInstance({
    id: dto.id,
    taskDefinitionId: dto.taskDefinitionId,
    name: dto.name,
    status: dto.status,
    assignedTo: dto.assignedTo,
    blockedReason: dto.blockedReason,
    activatedAt: dto.activatedAt,
    completedAt: dto.completedAt,
    skippedAt: dto.skippedAt,
    cancelledAt: dto.cancelledAt ?? undefined,
    cancelReason: dto.cancelReason ?? undefined,
    stepResults: (dto.stepResults ?? []).map(toStepResult),
  });
}

function fromTaskInstance(task: TaskInstance): TaskInstanceDto {
  return {
    id: task.id,
    taskDefinitionId: task.taskDefinitionId,
    name: task.name,
    status: task.status,
    assignedTo: task.assignedTo,
    blockedReason: task.blockedReason,
    activatedAt: task.activatedAt,
    completedAt: task.completedAt,
    skippedAt: task.skippedAt,
    cancelledAt: task.cancelledAt,
    cancelReason: task.cancelReason,
    stepResults: (task.stepResults ?? []).map(fromStepResult),
  };
}

function toArtifactInstance(dto: ArtifactInstanceDto): ArtifactInstance {
  return new ArtifactInstance({
    id: dto.id,
    artifactDefinitionId: dto.artifactDefinitionId,
    name: dto.name,
    type: dto.type,
    entityId: dto.entityId,
    stateMachineInstanceId: dto.stateMachineInstanceId,
    currentState: dto.currentState,
    updatedAt: dto.updatedAt,
  });
}

function fromArtifactInstance(artifact: ArtifactInstance): ArtifactInstanceDto {
  return {
    id: artifact.id,
    artifactDefinitionId: artifact.artifactDefinitionId,
    name: artifact.name,
    type: artifact.type,
    entityId: artifact.entityId,
    stateMachineInstanceId: artifact.stateMachineInstanceId,
    currentState: artifact.currentState,
    updatedAt: artifact.updatedAt,
  };
}
function toEventInstance(dto: EventInstanceDto): EventInstance {
  return new EventInstance({
    id: dto.id,
    eventUseId: dto.eventUseId,
    eventDefinitionId: dto.eventDefinitionId ?? undefined,
    name: dto.name ?? undefined,
    direction: dto.direction,
    status: dto.status,
    correlationValue: dto.correlationValue ?? undefined,
    waitingSince: dto.waitingSince ?? undefined,
    occurredAt: dto.occurredAt ?? undefined,
    payload: dto.payload ?? undefined,
    contextContribution: dto.contextContribution ?? undefined,
    dueAt: dto.dueAt ?? undefined,
    fireCount: dto.fireCount,
  });
}

function fromEventInstance(event: EventInstance): EventInstanceDto {
  return {
    id: event.id,
    eventUseId: event.eventUseId,
    eventDefinitionId: event.eventDefinitionId,
    name: event.name,
    direction: event.direction,
    status: event.status,
    correlationValue: event.correlationValue,
    waitingSince: event.waitingSince,
    occurredAt: event.occurredAt,
    payload: event.payload,
    contextContribution: event.contextContribution,
    dueAt: event.dueAt,
    fireCount: event.fireCount,
  };
}
// endregion
