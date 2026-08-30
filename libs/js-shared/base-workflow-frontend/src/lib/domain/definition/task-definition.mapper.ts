import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { PropertyMap } from '../property-map';
import { EntityReference, toReferenceIds } from '../reference-ids';
import { StepDefinition, TaskDefinition, TaskStepType } from './task-definition';

// region wire shapes — the schemas of base-workflow-api.yaml, exactly as they travel
interface StepDefinitionDto {
  id?: string;
  name?: string;
  description?: string;
  stepType?: TaskStepType;
  toolDefinitionId?: string;
  toolOperation?: string;
  inputMapping?: PropertyMap;
  outputMapping?: PropertyMap;
}

interface TaskDefinitionDto {
  id?: string;
  name?: string;
  description?: string;
  /**
   * All three are `string[]` by contract — role ids, artifact ids, artifact ids. They are typed wider
   * because the `RELATED_ENTITIES` control writes whole entities into its form control on selection —
   * see {@link toReferenceIds}.
   */
  performedByRoles?: EntityReference[];
  preconditionRuleId?: string;
  postconditionRuleId?: string;
  inputs?: EntityReference[];
  outputs?: EntityReference[];
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
 * **The three reference lists are flattened in both directions.** `performedByRoles`, `inputs` and
 * `outputs` are `RELATED_ENTITIES` controls — over roles and over artifacts — and that control writes
 * whole entities into its form control when the user picks one, while the contract wants `string[]`.
 * {@link toReferenceIds} is applied on the way in as well as out, so a payload holding embedded
 * objects loads as ids rather than half-flattening on the next save. That is not hypothetical for
 * `inputs` and `outputs`: they were modelled as typed `{ type, refId, label }` rows until this
 * revision, against a contract that has had them as plain artifact ids since the catalog split.
 *
 * **The steps are mapped element by element**, never passed through. An embedded row is edited as the
 * parsed JSON it arrived as, so a field the wire spelled differently from the model would leave its
 * control empty and silently drop the value on the next save — which is exactly what `toolId` did
 * against the contract's `toolDefinitionId`.
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
      inputs: toReferenceIds(source.inputs),
      outputs: toReferenceIds(source.outputs),
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
      inputs: toReferenceIds(entity.inputs),
      outputs: toReferenceIds(entity.outputs),
      steps: (entity.steps ?? []).map(fromStepDefinition),
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}

// region private helper functions
function toStepDefinition(dto: StepDefinitionDto): StepDefinition {
  return new StepDefinition({
    id: dto.id,
    name: dto.name,
    description: dto.description,
    stepType: dto.stepType,
    toolDefinitionId: dto.toolDefinitionId,
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
    stepType: step.stepType,
    toolDefinitionId: step.toolDefinitionId,
    toolOperation: step.toolOperation,
    inputMapping: step.inputMapping,
    outputMapping: step.outputMapping,
  };
}
// endregion
