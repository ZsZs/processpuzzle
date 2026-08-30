import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { ArtifactType } from '../definition/artifact-definition';
import { PropertyMap } from '../property-map';
import { ArtifactInstance, ProcessInstance, ProcessInstanceStatus, StepResult, TaskInstance, TaskInstanceStatus } from './process-instance';

// region wire shapes
interface StepResultDto {
  stepId?: string;
  completedAt?: string;
  toolResponse?: PropertyMap;
  error?: string;
}

interface TaskInstanceDto {
  id?: string;
  taskDefinitionId?: string;
  name?: string;
  status?: TaskInstanceStatus;
  assignedTo?: string;
  blockedReason?: string;
  activatedAt?: string;
  completedAt?: string;
  skippedAt?: string;
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

interface ProcessInstanceDto {
  id?: string;
  processDefinitionId?: string;
  processDefinitionName?: string;
  status?: ProcessInstanceStatus;
  entityId?: string;
  startedAt?: string;
  completedAt?: string;
  context?: PropertyMap;
  tasks?: TaskInstanceDto[];
  artifacts?: ArtifactInstanceDto[];
}
// endregion

/**
 * Translates between the `ProcessInstance` DTO of `base-workflow-api.yaml` and the entity the
 * generated screens render.
 *
 * `toDto` exists because `BaseEntityMapper` requires it, not because anything sends it: the contract
 * defines no `PUT /instances/{id}`, so the only writes are `POST /instances` — whose body is a
 * `StartProcessRequest`, a different schema entirely — and the three task verbs. It is implemented
 * faithfully rather than left throwing, so the store's optimistic paths and any future action surface
 * have a payload to build on; nothing today reaches the network with it.
 *
 * The nested rows are mapped element by element for the same reason as in the definition mapper: an
 * embedded row is edited as the JSON it arrived as, so a field spelled differently on the wire would
 * silently show blank.
 */
@Injectable({ providedIn: 'root' })
export class ProcessInstanceMapper implements BaseEntityMapper<ProcessInstance> {
  fromDto(dto: unknown): ProcessInstance {
    const source = dto as ProcessInstanceDto;
    return new ProcessInstance({
      id: source.id,
      processDefinitionId: source.processDefinitionId,
      processDefinitionName: source.processDefinitionName,
      status: source.status,
      entityId: source.entityId,
      startedAt: source.startedAt,
      completedAt: source.completedAt,
      context: source.context,
      tasks: (source.tasks ?? []).map(toTaskInstance),
      artifacts: (source.artifacts ?? []).map(toArtifactInstance),
    });
  }

  toDto(entity: ProcessInstance): ProcessInstanceDto {
    return {
      id: entity.id,
      processDefinitionId: entity.processDefinitionId,
      processDefinitionName: entity.processDefinitionName,
      status: entity.status,
      entityId: entity.entityId,
      startedAt: entity.startedAt,
      completedAt: entity.completedAt,
      context: entity.context,
      tasks: (entity.tasks ?? []).map(fromTaskInstance),
      artifacts: (entity.artifacts ?? []).map(fromArtifactInstance),
    };
  }
}

// region private helper functions
function toStepResult(dto: StepResultDto): StepResult {
  return new StepResult({ stepId: dto.stepId, completedAt: dto.completedAt, toolResponse: dto.toolResponse, error: dto.error });
}

function fromStepResult(result: StepResult): StepResultDto {
  return { stepId: result.stepId, completedAt: result.completedAt, toolResponse: result.toolResponse, error: result.error };
}

function toTaskInstance(dto: TaskInstanceDto): TaskInstance {
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
// endregion
