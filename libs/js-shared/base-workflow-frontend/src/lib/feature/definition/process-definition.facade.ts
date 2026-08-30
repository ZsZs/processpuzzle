import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { ProcessDefinition } from '../../domain/definition/process-definition';
import { createProcessDefinitionDescriptor } from '../../domain/definition/process-definition.descriptors';
import { ProcessDefinitionMapper } from '../../domain/definition/process-definition.mapper';
import { ProcessDefinitionService } from '../../domain/definition/process-definition.service';
import { ProcessDefinitionStore } from '../../domain/definition/process-definition.store';

@Injectable()
export class ProcessDefinitionFacade extends BaseEntityFacade<ProcessDefinition> {
  readonly entityType = ProcessDefinition;

  private readonly mapperRef = inject(ProcessDefinitionMapper);
  private readonly serviceRef = inject(ProcessDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return ProcessDefinitionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createProcessDefinitionDescriptor();
  }
}
