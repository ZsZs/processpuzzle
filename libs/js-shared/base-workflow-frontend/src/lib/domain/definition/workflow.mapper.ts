import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { EntityReference, toReferenceIds } from '../reference-ids';
import { Workflow, WorkflowTaskAssignment } from './workflow';

// region wire shapes — the schemas of base-workflow-api.yaml, exactly as they travel
interface WorkflowTaskAssignmentDto {
  taskDefinitionId?: string;
  performedBy?: string;
  dependsOn?: string[];
  parallel?: boolean;
  override?: boolean;
}

/**
 * `roles`, `artifacts` and `tools` are `string[]` by contract. They are typed wider here because the
 * `RELATED_ENTITIES` control writes whole entities into its form control on selection — see
 * {@link toReferenceIds}.
 */
interface WorkflowDto {
  id?: string;
  name?: string;
  description?: string;
  extends?: string;
  roles?: EntityReference[];
  artifacts?: EntityReference[];
  tools?: EntityReference[];
  tasks?: WorkflowTaskAssignmentDto[];
  activeInstances?: number;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}
// endregion

/**
 * Translates between the `Workflow` DTO of `base-workflow-api.yaml` and the entity the
 * generated screens work with.
 *
 * Three things are worth knowing about it.
 *
 * **The three reference lists are flattened in both directions.** `roles`, `artifacts` and `tools`
 * are `RELATED_ENTITIES` controls over catalog aggregates, and that control writes whole entities
 * into its form control when the user picks one, while the contract wants `string[]`.
 * {@link toReferenceIds} is applied on the way in as well as out, so a payload holding embedded
 * documents — as the pre-catalog contract did — loads as ids rather than half-flattening on the next
 * save.
 *
 * **`PUT /workflows/{workflowId}` is a full replacement**, so `toDto` emits all four lists
 * unconditionally — an absent one is an emptied workflow, not an untouched one. The assignments are
 * mapped element by element rather than passed through: an embedded row is edited as the parsed JSON
 * it arrived as, so a field the wire spelled differently from the model would leave its control empty
 * and silently drop the value on the next save.
 *
 * **`activeInstances` is read-only** and deliberately not emitted: the contract marks it
 * server-computed and the backend recounts it per list row. Sending it back would be sending a
 * derived value the server is about to overwrite. Everything else is listed field by field rather
 * than spread, so a control the form may gain later cannot leak into the payload unnoticed.
 */
@Injectable({ providedIn: 'root' })
export class WorkflowMapper implements BaseEntityMapper<Workflow> {
  fromDto(dto: unknown): Workflow {
    const source = dto as WorkflowDto;
    return new Workflow({
      id: source.id,
      name: source.name,
      description: source.description,
      extends: source.extends,
      roles: toReferenceIds(source.roles),
      artifacts: toReferenceIds(source.artifacts),
      tools: toReferenceIds(source.tools),
      tasks: (source.tasks ?? []).map(toWorkflowTaskAssignment),
      activeInstances: source.activeInstances,
      version: source.version,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    });
  }

  toDto(entity: Workflow): WorkflowDto {
    return {
      id: entity.id,
      name: entity.name,
      description: entity.description,
      extends: entity.extends,
      roles: toReferenceIds(entity.roles),
      artifacts: toReferenceIds(entity.artifacts),
      tools: toReferenceIds(entity.tools),
      tasks: (entity.tasks ?? []).map(fromWorkflowTaskAssignment),
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}

// region private helper functions
function toWorkflowTaskAssignment(dto: WorkflowTaskAssignmentDto): WorkflowTaskAssignment {
  return new WorkflowTaskAssignment({
    taskDefinitionId: dto.taskDefinitionId,
    performedBy: dto.performedBy,
    dependsOn: dto.dependsOn,
    parallel: dto.parallel,
    override: dto.override,
  });
}

/**
 * Both flags are written explicitly rather than left off when false: the PUT is a full replacement,
 * so an absent flag is an unset one, and the form's unticked checkbox has to say so.
 */
function fromWorkflowTaskAssignment(assignment: WorkflowTaskAssignment): WorkflowTaskAssignmentDto {
  return {
    taskDefinitionId: assignment.taskDefinitionId,
    performedBy: assignment.performedBy,
    dependsOn: assignment.dependsOn,
    parallel: assignment.parallel ?? false,
    override: assignment.override ?? false,
  };
}
// endregion
