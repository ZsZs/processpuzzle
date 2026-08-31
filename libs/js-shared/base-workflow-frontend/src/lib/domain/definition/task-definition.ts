import { BaseEntity } from '@processpuzzle/base-entity';
import { PropertyMap } from '../property-map';

/**
 * Frontend model of `TaskDefinition` — one unit of work, authored once per tenant under `/tasks` and
 * referenced by every workflow that needs it, together with the steps it carries.
 *
 * What is *not* here is the point. A shared task cannot say what has to finish before it, whether it
 * runs beside its siblings, or which one role performs it: all three answers belong to one workflow,
 * and they live on that workflow's `WorkflowTaskAssignment` instead. The task says who is *able* to
 * perform it — {@link TaskDefinition.performedByRoles} — and the workflow pins exactly one of them.
 *
 * A step stays nested, because it has no resource of its own: it travels inside the task's payload,
 * which the full-replacement `PUT /tasks/{taskId}` requires — a control that dropped it would wipe it
 * on the next save. What a task reads and writes does *not* stay nested: `inputs` and `outputs` are
 * plain artifact definition ids by contract, because an artifact's own `artifactType` already says
 * whether it is an entity, a document or a widget, so anything a task touches is declared as an
 * artifact of the organization first.
 *
 * {@link StepDefinition} is a class rather than an interface, because it is an embedded entity of its
 * own: `EmbeddedEntityFacade` mints the blank row an `Add` opens the child's form on, and that needs a
 * constructor. It stays plain data — the rows of a loaded task are the parsed JSON, never instances of
 * the class, so nothing may rely on `instanceof` or on a method.
 */

/**
 * Whether completing a step is a human act or a call the engine makes. `SERVICE_STEP` is the one that
 * reads {@link StepDefinition.toolDefinitionId} and {@link StepDefinition.toolOperation}; on a
 * `USER_STEP` those fields are ignored.
 */
export enum TaskStepType {
  USER_STEP = 'USER_STEP',
  SERVICE_STEP = 'SERVICE_STEP',
}

/**
 * One informal instruction inside a task. Free-text guidance the engine does not enforce — unless it
 * names a tool, in which case completing the step fires a REST call and the response is mapped back
 * into the workflow context.
 */
export class StepDefinition implements BaseEntity {
  id: string;
  name: string;
  description?: string;
  /** Whether the engine calls a tool here or a person does the work. */
  stepType?: TaskStepType;
  /** Id of a `Tool Definition`; ignored on a `USER_STEP`. */
  toolDefinitionId?: string;
  /** Operation id within {@link toolDefinitionId}. */
  toolOperation?: string;
  /** Tool parameter name → PPCL expression over the workflow context. */
  inputMapping?: PropertyMap;
  /** Context variable name → JSONPath into the tool response. */
  outputMapping?: PropertyMap;

  constructor(init: Partial<StepDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.description = init.description;
    this.stepType = init.stepType;
    this.toolDefinitionId = init.toolDefinitionId;
    this.toolOperation = init.toolOperation;
    this.inputMapping = init.inputMapping;
    this.outputMapping = init.outputMapping;
  }
}

/** One unit of work: who is able to perform it, what it reads and writes, and how it is carried out. */
export class TaskDefinition implements BaseEntity {
  id: string;
  name: string;
  description?: string;
  /**
   * `RoleDefinition.id`s able to perform this task. A list because the task is shared: each workflow
   * referencing it picks exactly one of these as that workflow's `performedBy`. Naming a role here
   * does not put the task in any workflow.
   */
  performedByRoles: string[];
  /** Id of a base-rule rule guarding activation; a false verdict keeps the task PENDING. */
  preconditionRuleId?: string;
  /** Id of a base-rule rule guarding completion; a false verdict keeps the task ACTIVE. */
  postconditionRuleId?: string;
  /** Ids of the `Artifact Definition`s this task reads. */
  inputs: string[];
  /** Ids of the `Artifact Definition`s this task produces or modifies. */
  outputs: string[];
  steps: StepDefinition[];
  // region server-assigned
  version: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  // endregion

  constructor(init: Partial<TaskDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.description = init.description;
    // Empty arrays rather than undefined, so the embedded step list always has something to append to
    // and each `RELATED_ENTITIES` control has a list to add the first pick to.
    this.performedByRoles = init.performedByRoles ?? [];
    this.preconditionRuleId = init.preconditionRuleId;
    this.postconditionRuleId = init.postconditionRuleId;
    this.inputs = init.inputs ?? [];
    this.outputs = init.outputs ?? [];
    this.steps = init.steps ?? [];
    this.version = init.version;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
