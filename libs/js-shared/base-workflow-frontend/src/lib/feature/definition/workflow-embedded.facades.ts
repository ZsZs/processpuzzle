import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { ArtifactUse, RequiredStartArtifact, RoleUse, ToolUse, WorkflowTaskAssignment } from '../../domain/definition/workflow';
import { createWorkflowTaskAssignmentDescriptor } from '../../domain/definition/workflow-task-assignment.descriptors';
import { createWorkflowArtifactUseDescriptor, createWorkflowRoleUseDescriptor, createWorkflowToolUseDescriptor } from '../../domain/definition/workflow-use.descriptors';
import { createRequiredStartArtifactDescriptor } from '../../domain/definition/required-start-artifact.descriptors';
import { StepDefinition } from '../../domain/definition/task-definition';
import { createStepDefinitionDescriptor } from '../../domain/definition/step-definition.descriptors';
import { ToolOperation } from '../../domain/definition/tool-definition';
import { createToolOperationDescriptor } from '../../domain/definition/tool-operation.descriptors';

/**
 * The embedded levels of the definition layer, in one module because they are one decision: each has
 * a facade like any other entity — that is what gives it a store, and so a working list and form —
 * and only its repository differs, reading and writing the aggregate's document rather than an
 * endpoint of its own.
 *
 * Five of the seven belong to the workflow — its task assignments, the three `*Use` rows and the
 * required artifacts of its start condition — and two to a task and a tool. That distribution is the
 * shape of the reference model: nothing embedded is shared, which is precisely why it stayed embedded.
 * A role, an artifact, a task and a tool each moved out to a catalog aggregate of its own once more
 * than one workflow needed it; what stayed behind is the workflow's *use* of them.
 *
 * The three `*Use` facades cannot be collapsed into one even though the rows are the same shape over a
 * different target: the store a facade builds is keyed by the descriptor's entity name, and `roles`,
 * `artifacts` and `tools` are three lists whose rows have to stay apart. Same arrangement as
 * base-state's guards and actions over one `BeanRef`.
 */

@Injectable()
export class WorkflowTaskAssignmentFacade extends EmbeddedEntityFacade<WorkflowTaskAssignment> {
  readonly entityType = WorkflowTaskAssignment;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWorkflowTaskAssignmentDescriptor();
  }
}

@Injectable()
export class WorkflowRoleUseFacade extends EmbeddedEntityFacade<RoleUse> {
  readonly entityType = RoleUse;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWorkflowRoleUseDescriptor();
  }
}

@Injectable()
export class WorkflowArtifactUseFacade extends EmbeddedEntityFacade<ArtifactUse> {
  readonly entityType = ArtifactUse;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWorkflowArtifactUseDescriptor();
  }
}

@Injectable()
export class WorkflowToolUseFacade extends EmbeddedEntityFacade<ToolUse> {
  readonly entityType = ToolUse;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWorkflowToolUseDescriptor();
  }
}

@Injectable()
export class WorkflowRequiredStartArtifactFacade extends EmbeddedEntityFacade<RequiredStartArtifact> {
  readonly entityType = RequiredStartArtifact;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createRequiredStartArtifactDescriptor();
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
