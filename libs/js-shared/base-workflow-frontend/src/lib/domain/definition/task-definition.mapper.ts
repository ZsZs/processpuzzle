import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { PropertyMap } from '../property-map';
import { EntityReference, toReferenceIds } from '../reference-ids';
import { ReferenceType, StepDefinition, TaskDefinition, TaskIOReference } from './task-definition';

// region wire shapes — the schemas of base-workflow-api.yaml, exactly as they travel
interface TaskIOReferenceDto {
  type?: ReferenceType;
  refId?: string;
  label?: string;
}

interface StepDefinitionDto {
  id?: string;
  name?: string;
  description?: string;
  toolId?: string;
  toolOperation?: string;
  inputMapping?: PropertyMap;
  outputMapping?: PropertyMap;
}

interface TaskDefinitionDto {
  id?: string;
  name?: string;
  description?: string;
  /** `string[]` by contract. The form's control may hold whole roles — see {@link toReferenceIds}. */
  performedByRoles?: EntityReference[];
  preconditionRuleId?: string;
  postconditionRuleId?: string;
  inputs?: TaskIOReferenceDto[];
  outputs?: TaskIOReferenceDto[];
  steps?: StepDefinitionDto[];
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}
// endregion

/**
 * Translates between the `TaskDefinition` DTO of `base-workflow-api.yaml` and the entity the
 * generated screens work with. Three things are worth knowing about it.
 *
 * **`performedByRoles` is flattened in both directions.** The attribute is a `RELATED_ENTITIES`
 * control over `Workflow Role Definition`, and that control writes whole entities into its form
 * control when the user picks one, while the contract wants `string[]`. {@link toReferenceIds} is
 * applied on the way in as well as out, so a payload holding embedded roles loads as ids rather than
 * half-flattening on the next save.
 *
 * **The nested rows are mapped element by element**, never passed through. An embedded row is edited
 * as the parsed JSON it arrived as, so a field the wire spelled differently from the model would
 * leave its control empty and silently drop the value on the next save. Mapping each row is what
 * keeps that class of bug out of every descriptor, even while every field happens to agree today.
 *
 * **`PUT /tasks/{taskId}` is a full replacement**, so `toDto` emits `inputs`, `outputs`, `steps` and
 * `performedByRoles` unconditionally — an absent list is an emptied task, not an untouched one.
 */
@Injectable({ providedIn: 'root' })
export class TaskDefinitionMapper implements BaseEntityMapper<TaskDefinition> {
  fromDto(dto: unknown): TaskDefinition {
    const source = dto as TaskDefinitionDto;
    return new TaskDefinition({
      id: source.id,
      name: source.name,
      description: source.description,
      performedByRoles: toReferenceIds(source.performedByRoles),
      preconditionRuleId: source.preconditionRuleId,
      postconditionRuleId: source.postconditionRuleId,
      inputs: (source.inputs ?? []).map(toTaskIOReference),
      outputs: (source.outputs ?? []).map(toTaskIOReference),
      steps: (source.steps ?? []).map(toStepDefinition),
      version: source.version,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    });
  }

  toDto(entity: TaskDefinition): TaskDefinitionDto {
    return {
      id: entity.id,
      name: entity.name,
      description: entity.description,
      performedByRoles: toReferenceIds(entity.performedByRoles),
      preconditionRuleId: entity.preconditionRuleId,
      postconditionRuleId: entity.postconditionRuleId,
      inputs: (entity.inputs ?? []).map(fromTaskIOReference),
      outputs: (entity.outputs ?? []).map(fromTaskIOReference),
      steps: (entity.steps ?? []).map(fromStepDefinition),
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}

// region private helper functions
function toTaskIOReference(dto: TaskIOReferenceDto): TaskIOReference {
  return new TaskIOReference({ type: dto.type, refId: dto.refId, label: dto.label });
}

function fromTaskIOReference(reference: TaskIOReference): TaskIOReferenceDto {
  return { type: reference.type, refId: reference.refId, label: reference.label };
}

function toStepDefinition(dto: StepDefinitionDto): StepDefinition {
  return new StepDefinition({
    id: dto.id,
    name: dto.name,
    description: dto.description,
    toolId: dto.toolId,
    toolOperation: dto.toolOperation,
    inputMapping: dto.inputMapping,
    outputMapping: dto.outputMapping,
  });
}

function fromStepDefinition(step: StepDefinition): StepDefinitionDto {
  return {
    id: step.id,
    name: step.name,
    description: step.description,
    toolId: step.toolId,
    toolOperation: step.toolOperation,
    inputMapping: step.inputMapping,
    outputMapping: step.outputMapping,
  };
}
// endregion
