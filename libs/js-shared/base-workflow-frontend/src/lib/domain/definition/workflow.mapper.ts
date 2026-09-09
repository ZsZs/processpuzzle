import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { PropertyMap } from '../property-map';
import { EntityReference, toReferenceIds } from '../reference-ids';
import { ArtifactUse, JoinType, RequiredStartArtifact, RoleUse, ToolUse, Workflow, WorkflowStartConditionType, WorkflowTaskAssignment } from './workflow';

// region wire shapes — the schemas of base-workflow-api.yaml, exactly as they travel
interface RoleUseDto {
  roleDefinitionId?: string;
}

interface ArtifactUseDto {
  artifactDefinitionId?: string;
}

interface ToolUseDto {
  toolDefinitionId?: string;
}

interface RequiredStartArtifactDto {
  artifactDefinitionId?: string;
  state?: string;
}

/**
 * `authorizedRoles` is `string[]` by contract. It is typed wider here because the `RELATED_ENTITIES`
 * control writes whole entities into its form control on selection — see {@link toReferenceIds}.
 */
interface WorkflowStartConditionDto {
  startType?: WorkflowStartConditionType;
  requiredArtifacts?: RequiredStartArtifactDto[];
  eventType?: string;
  payloadMapping?: PropertyMap;
  authorizedRoles?: EntityReference[];
  milestoneRef?: string;
  preconditionExpression?: string;
}

interface WorkflowTaskAssignmentDto {
  taskDefinitionId?: string;
  performedBy?: string;
  dependsOn?: string[];
  joinType?: JoinType;
  parallel?: boolean;
  override?: boolean;
}

interface WorkflowDto {
  id?: string;
  name?: string;
  description?: string;
  extends?: string;
  startCondition?: WorkflowStartConditionDto;
  roles?: RoleUseDto[];
  artifacts?: ArtifactUseDto[];
  tools?: ToolUseDto[];
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
 * Four things are worth knowing about it.
 *
 * **The four embedded lists are mapped element by element**, never passed through. An embedded row is
 * edited as the parsed JSON it arrived as, so a field the wire spelled differently from the model would
 * leave its control empty and silently drop the value on the next save. That is not hypothetical here:
 * `roles`, `artifacts` and `tools` were modelled as id arrays until this revision, while the contract
 * has them as `RoleUse` / `ArtifactUse` / `ToolUse` objects wrapping a definition id — so every role,
 * artifact and tool of a loaded workflow vanished, and the next save wrote `string[]` where the backend
 * expects objects.
 *
 * **`startCondition` is flattened and re-nested**, the same arrangement `ToolDefinitionMapper` uses for
 * `auth`: `fromDto` lifts the six scalar fields and the required-artifact rows onto the entity so the
 * generic form can build one control per field, and `toDto` rebuilds the nested object. It is emitted
 * as `undefined` when no `startType` was chosen, because a workflow may legitimately have no start
 * condition and an object carrying only nulls is not the same statement.
 *
 * **`PUT /workflows/{workflowId}` is a full replacement**, so `toDto` emits all four lists
 * unconditionally — an absent one is an emptied workflow, not an untouched one. It is also why every
 * contract field has to be modelled even if the form never edits it: a field the mapper does not carry
 * is a field the next save deletes.
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
    const startCondition = source.startCondition;
    return new Workflow({
      id: source.id,
      name: source.name,
      description: source.description,
      extends: source.extends,
      startType: startCondition?.startType,
      requiredArtifacts: (startCondition?.requiredArtifacts ?? []).map(toRequiredStartArtifact),
      eventType: startCondition?.eventType,
      payloadMapping: startCondition?.payloadMapping,
      authorizedRoles: toReferenceIds(startCondition?.authorizedRoles),
      milestoneRef: startCondition?.milestoneRef,
      preconditionExpression: startCondition?.preconditionExpression,
      roles: (source.roles ?? []).map(toRoleUse),
      artifacts: (source.artifacts ?? []).map(toArtifactUse),
      tools: (source.tools ?? []).map(toToolUse),
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
      startCondition: toStartConditionDto(entity),
      roles: (entity.roles ?? []).map(fromRoleUse),
      artifacts: (entity.artifacts ?? []).map(fromArtifactUse),
      tools: (entity.tools ?? []).map(fromToolUse),
      tasks: (entity.tasks ?? []).map(fromWorkflowTaskAssignment),
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}

// region private helper functions
function toRoleUse(dto: RoleUseDto): RoleUse {
  return new RoleUse({ roleDefinitionId: dto.roleDefinitionId });
}

function fromRoleUse(use: RoleUse): RoleUseDto {
  return { roleDefinitionId: use.roleDefinitionId };
}

function toArtifactUse(dto: ArtifactUseDto): ArtifactUse {
  return new ArtifactUse({ artifactDefinitionId: dto.artifactDefinitionId });
}

function fromArtifactUse(use: ArtifactUse): ArtifactUseDto {
  return { artifactDefinitionId: use.artifactDefinitionId };
}

function toToolUse(dto: ToolUseDto): ToolUse {
  return new ToolUse({ toolDefinitionId: dto.toolDefinitionId });
}

function fromToolUse(use: ToolUse): ToolUseDto {
  return { toolDefinitionId: use.toolDefinitionId };
}

function toRequiredStartArtifact(dto: RequiredStartArtifactDto): RequiredStartArtifact {
  return new RequiredStartArtifact({ artifactDefinitionId: dto.artifactDefinitionId, state: dto.state });
}

function fromRequiredStartArtifact(artifact: RequiredStartArtifact): RequiredStartArtifactDto {
  return { artifactDefinitionId: artifact.artifactDefinitionId, state: artifact.state };
}

/**
 * Re-nests the seven flattened start-condition fields, or answers `undefined` when the author chose no
 * `startType`.
 *
 * `startType` is the contract's only required field of the object, so it is what decides whether there
 * is an object at all: a workflow without a start condition can only be started explicitly through
 * `/instances`, and that is a different statement from one whose condition is present but blank.
 */
function toStartConditionDto(entity: Workflow): WorkflowStartConditionDto | undefined {
  if (!entity.startType) {
    return undefined;
  }

  return {
    startType: entity.startType,
    requiredArtifacts: (entity.requiredArtifacts ?? []).map(fromRequiredStartArtifact),
    eventType: entity.eventType,
    payloadMapping: entity.payloadMapping,
    authorizedRoles: toReferenceIds(entity.authorizedRoles),
    milestoneRef: entity.milestoneRef,
    preconditionExpression: entity.preconditionExpression,
  };
}

function toWorkflowTaskAssignment(dto: WorkflowTaskAssignmentDto): WorkflowTaskAssignment {
  return new WorkflowTaskAssignment({
    taskDefinitionId: dto.taskDefinitionId,
    performedBy: dto.performedBy,
    dependsOn: dto.dependsOn,
    joinType: dto.joinType,
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
    joinType: assignment.joinType,
    parallel: assignment.parallel ?? false,
    override: assignment.override ?? false,
  };
}
// endregion
