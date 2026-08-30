import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { ProcessTaskAssignment } from '../../domain/definition/process-definition';
import { createProcessTaskAssignmentDescriptor } from '../../domain/definition/process-task-assignment.descriptors';
import { StepDefinition, TaskIOReference } from '../../domain/definition/task-definition';
import { createStepDefinitionDescriptor } from '../../domain/definition/step-definition.descriptors';
import { createTaskInputReferenceDescriptor, createTaskOutputReferenceDescriptor } from '../../domain/definition/task-io-reference.descriptors';
import { ToolOperation } from '../../domain/definition/tool-definition';
import { createToolOperationDescriptor } from '../../domain/definition/tool-operation.descriptors';

/**
 * The embedded levels of the definition layer, in one module because they are one decision: each has
 * a facade like any other entity — that is what gives it a store, and so a working list and form —
 * and only its repository differs, reading and writing the aggregate's document rather than an
 * endpoint of its own.
 *
 * All four belong to a different parent, and that is the shape of the reference model: an assignment
 * to a process, a reference and a step to a task, an operation to a tool. Nothing embedded is shared,
 * which is precisely why it stayed embedded — a role, an artifact, a task and a tool each moved out to
 * a catalog aggregate of its own once more than one process needed it.
 *
 * A task's inputs and its outputs share `TaskIOReference` as their entity type and differ only in
 * their descriptor. That is deliberate and it is also the reason they cannot share a *facade*: the
 * store a facade builds is keyed by the descriptor's entity name, and `inputs` and `outputs` are two
 * lists whose rows have to stay apart. Same arrangement as base-state's guards and actions over one
 * `BeanRef`.
 */

@Injectable()
export class ProcessTaskAssignmentFacade extends EmbeddedEntityFacade<ProcessTaskAssignment> {
  readonly entityType = ProcessTaskAssignment;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createProcessTaskAssignmentDescriptor();
  }
}

@Injectable()
export class TaskInputReferenceFacade extends EmbeddedEntityFacade<TaskIOReference> {
  readonly entityType = TaskIOReference;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createTaskInputReferenceDescriptor();
  }
}

@Injectable()
export class TaskOutputReferenceFacade extends EmbeddedEntityFacade<TaskIOReference> {
  readonly entityType = TaskIOReference;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createTaskOutputReferenceDescriptor();
  }
}

@Injectable()
export class TaskStepDefinitionFacade extends EmbeddedEntityFacade<StepDefinition> {
  readonly entityType = StepDefinition;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createStepDefinitionDescriptor();
  }
}

@Injectable()
export class ToolOperationFacade extends EmbeddedEntityFacade<ToolOperation> {
  readonly entityType = ToolOperation;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createToolOperationDescriptor();
  }
}
