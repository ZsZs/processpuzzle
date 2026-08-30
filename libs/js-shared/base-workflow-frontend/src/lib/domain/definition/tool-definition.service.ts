import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { ToolDefinition } from './tool-definition';
import { ToolDefinitionMapper } from './tool-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/tools`. A resource of its own rather than a sub-resource of
 * a process, exactly as the contract has it: a tool is shared across process definitions, and a
 * step's `toolId` is the reference that ties the two together.
 */
@Injectable({ providedIn: 'root' })
export class ToolDefinitionService extends BaseEntityRestService<ToolDefinition> {
  constructor(protected override entityMapper: ToolDefinitionMapper) {
    super(entityMapper, 'WORKFLOW_SERVICE_ROOT', 'tools');
  }
}
