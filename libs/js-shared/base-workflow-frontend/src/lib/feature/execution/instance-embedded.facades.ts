import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { ArtifactInstance, StepResult, TaskInstance } from '../../domain/execution/workflow-instance';
import { createStepResultDescriptor } from '../../domain/execution/step-result.descriptors';
import { createTaskInstanceDescriptor } from '../../domain/execution/task-instance.descriptors';
import { createArtifactInstanceDescriptor } from '../../domain/execution/artifact-instance.descriptors';

/**
 * The three embedded levels of a running workflow. Facades like any other, even though the screens
 * they drive are read-only: what makes them read-only is their descriptors — `isAbstract`, and every
 * attribute `disabled` — not a narrower facade. Keeping them ordinary is what lets the rows be
 * reached, listed and deep-linked exactly as an editable child would be.
 */

@Injectable()
export class TaskInstanceFacade extends EmbeddedEntityFacade<TaskInstance> {
  readonly entityType = TaskInstance;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createTaskInstanceDescriptor();
  }
}

@Injectable()
export class ArtifactInstanceFacade extends EmbeddedEntityFacade<ArtifactInstance> {
  readonly entityType = ArtifactInstance;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createArtifactInstanceDescriptor();
  }
}

@Injectable()
export class TaskStepResultFacade extends EmbeddedEntityFacade<StepResult> {
  readonly entityType = StepResult;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createStepResultDescriptor();
  }
}
