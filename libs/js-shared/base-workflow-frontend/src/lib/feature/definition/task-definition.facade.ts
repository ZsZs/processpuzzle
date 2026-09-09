import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { TaskDefinition } from '../../domain/definition/task-definition';
import { createTaskDefinitionDescriptor } from '../../domain/definition/task-definition.descriptors';
import { TaskDefinitionMapper } from '../../domain/definition/task-definition.mapper';
import { TaskDefinitionService } from '../../domain/definition/task-definition.service';
import { TaskDefinitionStore } from '../../domain/definition/task-definition.store';

@Injectable()
export class TaskDefinitionFacade extends BaseEntityFacade<TaskDefinition> {
  readonly entityType = TaskDefinition;

  private readonly mapperRef = inject(TaskDefinitionMapper);
  private readonly serviceRef = inject(TaskDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return TaskDefinitionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createTaskDefinitionDescriptor();
  }
}
