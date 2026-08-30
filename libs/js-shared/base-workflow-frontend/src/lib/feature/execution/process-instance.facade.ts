import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { ProcessInstance } from '../../domain/execution/process-instance';
import { createProcessInstanceDescriptor } from '../../domain/execution/process-instance.descriptors';
import { ProcessInstanceMapper } from '../../domain/execution/process-instance.mapper';
import { ProcessInstanceService } from '../../domain/execution/process-instance.service';
import { ProcessInstanceStore } from '../../domain/execution/process-instance.store';

@Injectable()
export class ProcessInstanceFacade extends BaseEntityFacade<ProcessInstance> {
  readonly entityType = ProcessInstance;

  private readonly mapperRef = inject(ProcessInstanceMapper);
  private readonly serviceRef = inject(ProcessInstanceService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return ProcessInstanceStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createProcessInstanceDescriptor();
  }
}
