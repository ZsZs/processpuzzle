import { BaseEntity } from '@processpuzzle/base-entity';
import { PropertyMap } from '../property-map';

/**
 * Frontend model of `TaskDefinition` — one unit of work, authored once per tenant under `/tasks` and
 * referenced by every process that needs it, together with the references and steps it carries.
 *
 * What is *not* here is the point. A shared task cannot say what has to finish before it, whether it
 * runs beside its siblings, or which one role performs it: all three answers belong to one process,
 * and they live on that process's `ProcessTaskAssignment` instead. The task says who is *able* to
 * perform it — {@link TaskDefinition.performedByRoles} — and the process pins exactly one of them.
 *
 * A reference and a step stay nested, because neither has a resource of its own: they travel inside
 * the task's payload, which the full-replacement `PUT /tasks/{taskId}` requires — a control that
 * dropped them would wipe them on the next save.
 *
 * The nested definitions are classes rather than interfaces, because each is an embedded entity of
 * its own: `EmbeddedEntityFacade` mints the blank row an `Add` opens the child's form on, and that
 * needs a constructor. They stay plain data — the rows of a loaded task are the parsed JSON, never
 * instances of these classes, so nothing may rely on `instanceof` or on a method.
 */

/** What kind of resource a {@link TaskIOReference} points at. Mirrors the contract's `ReferenceType`. */
export enum ReferenceType {
  /** An `ArtifactDefinition` of this organization — the only kind whose lifecycle base-state governs. */
  ARTIFACT = 'ARTIFACT',
  BASE_ENTITY = 'BASE_ENTITY',
  DOCUMENT = 'DOCUMENT',
  WIDGET = 'WIDGET',
}

/**
 * A resource a task reads or writes: an artifact definition, a base-entity entity, a base-artifact
 * document or a registered widget, told apart by {@link type}.
 *
 * `refId` identifies it, which is why the class declares but never assigns `id`: the contract gives a
 * reference no key of its own. `declare` emits nothing, so the payload stays exactly the shape the
 * schema describes; it is there because `BaseEntity`'s only property is an optional `id` and
 * TypeScript's weak-type rule rejects a type that shares no property with it.
 */
export class TaskIOReference implements BaseEntity {
  declare readonly id?: string;

  type: ReferenceType | undefined;
  refId: string;
  /** Display label overriding the referenced resource's own name. */
  label?: string;

  constructor(init: Partial<TaskIOReference> = {}) {
    this.type = init.type;
    this.refId = init.refId ?? '';
    this.label = init.label;
  }
}

/**
 * One informal instruction inside a task. Free-text guidance the engine does not enforce — unless it
 * names a tool, in which case completing the step fires a REST call and the response is mapped back
 * into the process context.
 */
export class StepDefinition implements BaseEntity {
  id: string;
  name: string;
  description?: string;
  /** Id of a `Tool Definition`; empty for a purely manual step. */
  toolId?: string;
  /** Operation id within {@link toolId}. */
  toolOperation?: string;
  /** Tool parameter name → PPCL expression over the process context. */
  inputMapping?: PropertyMap;
  /** Context variable name → JSONPath into the tool response. */
  outputMapping?: PropertyMap;

  constructor(init: Partial<StepDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.description = init.description;
    this.toolId = init.toolId;
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
   * `RoleDefinition.id`s able to perform this task. A list because the task is shared: each process
   * referencing it picks exactly one of these as that process's `performedBy`. Naming a role here
   * does not put the task in any process.
   */
  performedByRoles: string[];
  /** Id of a base-rule rule guarding activation; a false verdict keeps the task PENDING. */
  preconditionRuleId?: string;
  /** Id of a base-rule rule guarding completion; a false verdict keeps the task ACTIVE. */
  postconditionRuleId?: string;
  inputs: TaskIOReference[];
  outputs: TaskIOReference[];
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
    // Empty arrays rather than undefined, so an embedded list always has something to append to and
    // the `RELATED_ENTITIES` control over the roles has a list to add the first pick to.
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
