import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { TaskDefinition } from './task-definition';
import { TaskDefinitionMapper } from './task-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/tasks`. A resource of its own rather than a sub-resource of
 * a process: a task is authored once and referenced by every process that needs it, through a
 * `ProcessTaskAssignment` naming its id.
 *
 * A task's own children — its inputs, outputs and steps — have no endpoint of their own and travel
 * inside this resource's payload, which is why the generic screens edit them as embedded components
 * of this entity.
 */
@Injectable({ providedIn: 'root' })
export class TaskDefinitionService extends BaseEntityRestService<TaskDefinition> {
  constructor(protected override entityMapper: TaskDefinitionMapper) {
    super(entityMapper, 'WORKFLOW_SERVICE_ROOT', 'tasks');
  }
}
