import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { RoleDefinition } from './role-definition';
import { RoleDefinitionMapper } from './role-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/roles`. A resource of its own rather than a sub-resource of
 * a workflow, exactly as the contract has it: a role is shared across workflow definitions, and a
 * workflow's `roles` list is the reference that ties the two together.
 */
@Injectable({ providedIn: 'root' })
export class RoleDefinitionService extends BaseEntityRestService<RoleDefinition> {
  constructor(protected override entityMapper: RoleDefinitionMapper) {
    super(entityMapper, 'WORKFLOW_SERVICE_ROOT', 'roles');
  }
}
