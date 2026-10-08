import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { PropertyMap } from '../property-map';
import { EntityReference, toReferenceIds } from '../reference-ids';
import { ArtifactUse, JoinType, RequiredStartArtifact, RoleUse, StartEvent, TaskArtifactState, ToolUse, Workflow, WorkflowStartConditionType, WorkflowTaskAssignment } from './workflow';

// region wire shapes — the schemas of base-workflow-api.yaml, exactly as they travel
interface RoleUseDto {
  roleDefinitionId?: string;
}

interface ArtifactUseDto {
  artifactDefinitionId?: string;
  objectName?: string | null;
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
interface StartEventDto {
  id?: string;
  name?: string;
  startType?: WorkflowStartConditionType;
  requiredArtifacts?: RequiredStartArtifactDto[];
  eventType?: string;
  payloadMapping?: PropertyMap;
  authorizedRoles?: EntityReference[];
  milestoneRef?: string;
  preconditionExpression?: string;
}

/**
 * `artifactDefinitionId` is a plain id by contract, typed wider for the same reason as
 * {@link StartEventDto.authorizedRoles}: the `FOREIGN_KEY` control may write the picked entity itself.
 */
interface TaskArtifactStateDto {
  artifactDefinitionId?: EntityReference;
  inputState?: string | null;
  outputState?: string | null;
}

interface WorkflowTaskAssignmentDto {
  taskDefinitionId?: string;
  performedBy?: string;
  dependsOn?: string[];
  joinType?: JoinType;
  parallel?: boolean;
  override?: boolean;
  artifactStates?: TaskArtifactStateDto[];
}

interface WorkflowDto {
  id?: string;
  name?: string;
  description?: string;
  extends?: string;
  startEvents?: StartEventDto[];
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
 * **The five embedded lists are mapped element by element**, never passed through. An embedded row is
 * edited as the parsed JSON it arrived as, so a field the wire spelled differently from the model would
 * leave its control empty and silently drop the value on the next save. That is not hypothetical here:
 * `roles`, `artifacts` and `tools` were modelled as id arrays until this revision, while the contract
 * has them as `RoleUse` / `ArtifactUse` / `ToolUse` objects wrapping a definition id — so every role,
 * artifact and tool of a loaded workflow vanished, and the next save wrote `string[]` where the backend
 * expects objects.
 *
 * **`startEvents` is a list of rows like any other**, each nesting its own `requiredArtifacts`. It
 * replaced a single, optional `startCondition` object that this mapper used to flatten onto the
 * workflow's form; a workflow with several entry points needs one row per entry point, and a row is
 * what the generic screens already know how to edit. A workflow with none is an empty list, not an
 * absent one — the PUT would otherwise read it as untouched.
 *
 * **`PUT /workflows/{workflowId}` is a full replacement**, so `toDto` emits all five lists
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
    return new Workflow({
      id: source.id,
      name: source.name,
      description: source.description,
      extends: source.extends,
      startEvents: (source.startEvents ?? []).map(toStartEvent),
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
      startEvents: (entity.startEvents ?? []).map(fromStartEvent),
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
  return new ArtifactUse({ artifactDefinitionId: dto.artifactDefinitionId, objectName: dto.objectName ?? undefined });
}

function fromArtifactUse(use: ArtifactUse): ArtifactUseDto {
  return { artifactDefinitionId: use.artifactDefinitionId, objectName: use.objectName || undefined };
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

function toStartEvent(dto: StartEventDto): StartEvent {
  return new StartEvent({
    id: dto.id,
    name: dto.name,
    startType: dto.startType,
    requiredArtifacts: (dto.requiredArtifacts ?? []).map(toRequiredStartArtifact),
    eventType: dto.eventType,
    payloadMapping: dto.payloadMapping,
    authorizedRoles: toReferenceIds(dto.authorizedRoles),
    milestoneRef: dto.milestoneRef,
    preconditionExpression: dto.preconditionExpression,
  });
}

/**
 * Every field is written whatever the `startType`: the backend ignores the ones the type does not read,
 * and dropping them here would lose what the author typed the moment they try another type and back.
 */
function fromStartEvent(event: StartEvent): StartEventDto {
  return {
    id: event.id,
    name: event.name,
    startType: event.startType,
    requiredArtifacts: (event.requiredArtifacts ?? []).map(fromRequiredStartArtifact),
    eventType: event.eventType,
    payloadMapping: event.payloadMapping,
    authorizedRoles: toReferenceIds(event.authorizedRoles),
    milestoneRef: event.milestoneRef,
    preconditionExpression: event.preconditionExpression,
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
    artifactStates: (dto.artifactStates ?? []).map(toTaskArtifactState),
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
    artifactStates: (assignment.artifactStates ?? []).map(fromTaskArtifactState),
  };
}

/** One reference as its id — `''` for none, which is what a blank row's required control holds. */
function referenceIdOf(reference: EntityReference | undefined): string {
  return reference === undefined ? '' : (toReferenceIds([reference])[0] ?? '');
}

function toTaskArtifactState(dto: TaskArtifactStateDto): TaskArtifactState {
  return new TaskArtifactState({
    artifactDefinitionId: referenceIdOf(dto.artifactDefinitionId),
    inputState: dto.inputState ?? undefined,
    outputState: dto.outputState ?? undefined,
  });
}

/**
 * A blank state is sent as absent rather than as `''`: a cleared text box writes the empty string, and
 * the backend would read that as a state named nothing rather than as no state.
 */
function fromTaskArtifactState(state: TaskArtifactState): TaskArtifactStateDto {
  return {
    artifactDefinitionId: referenceIdOf(state.artifactDefinitionId),
    inputState: state.inputState?.trim() || undefined,
    outputState: state.outputState?.trim() || undefined,
  };
}
// endregion
