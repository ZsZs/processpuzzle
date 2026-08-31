import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { WorkflowInstance } from '../../domain/execution/workflow-instance';
import { createWorkflowInstanceDescriptor } from '../../domain/execution/workflow-instance.descriptors';
import { WorkflowInstanceMapper } from '../../domain/execution/workflow-instance.mapper';
import { WorkflowInstanceService } from '../../domain/execution/workflow-instance.service';
import { WorkflowInstanceStore } from '../../domain/execution/workflow-instance.store';

@Injectable()
export class WorkflowInstanceFacade extends BaseEntityFacade<WorkflowInstance> {
  readonly entityType = WorkflowInstance;

  private readonly mapperRef = inject(WorkflowInstanceMapper);
  private readonly serviceRef = inject(WorkflowInstanceService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return WorkflowInstanceStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWorkflowInstanceDescriptor();
  }
}
