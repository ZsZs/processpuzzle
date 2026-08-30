import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { Workflow } from '../../domain/definition/workflow';
import { createWorkflowDescriptor } from '../../domain/definition/workflow.descriptors';
import { WorkflowMapper } from '../../domain/definition/workflow.mapper';
import { WorkflowService } from '../../domain/definition/workflow.service';
import { WorkflowStore } from '../../domain/definition/workflow.store';

@Injectable()
export class WorkflowFacade extends BaseEntityFacade<Workflow> {
  readonly entityType = Workflow;

  private readonly mapperRef = inject(WorkflowMapper);
  private readonly serviceRef = inject(WorkflowService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return WorkflowStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWorkflowDescriptor();
  }
}
