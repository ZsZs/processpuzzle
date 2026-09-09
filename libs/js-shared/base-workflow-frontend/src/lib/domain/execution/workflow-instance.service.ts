import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { WorkflowInstance } from './workflow-instance';
import { WorkflowInstanceMapper } from './workflow-instance.mapper';

/**
 * Read access to `/organizations/{orgKey}/instances`.
 *
 * Only the two reads of `BaseEntityRestService` are exercised — the collection GET behind the list and
 * the single GET behind a deep link — because the contract defines no `PUT` here. The inherited
 * `add`, `update` and `delete` are left in place rather than overridden to throw: `delete` happens to
 * be exactly `cancelWorkflowInstance` (`DELETE /instances/{id}`), and the screens never offer either,
 * since every descriptor in this folder is `isAbstract`.
 *
 * `startWorkflowInstance` is not here at all. It posts a `StartWorkflowRequest` — a workflow definition
 * id, an optional entity id and an initial context — which is a different schema from the instance it
 * returns, so it belongs to whatever surface starts a workflow rather than to the repository of the
 * entity it creates.
 */
@Injectable({ providedIn: 'root' })
export class WorkflowInstanceService extends BaseEntityRestService<WorkflowInstance> {
  constructor(protected override entityMapper: WorkflowInstanceMapper) {
    super(entityMapper, 'WORKFLOW_SERVICE_ROOT', 'instances');
  }
}
