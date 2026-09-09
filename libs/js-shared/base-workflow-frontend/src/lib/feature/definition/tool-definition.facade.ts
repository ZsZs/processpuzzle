import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { ToolDefinition } from '../../domain/definition/tool-definition';
import { createToolDefinitionDescriptor } from '../../domain/definition/tool-definition.descriptors';
import { ToolDefinitionMapper } from '../../domain/definition/tool-definition.mapper';
import { ToolDefinitionService } from '../../domain/definition/tool-definition.service';
import { ToolDefinitionStore } from '../../domain/definition/tool-definition.store';

@Injectable()
export class ToolDefinitionFacade extends BaseEntityFacade<ToolDefinition> {
  readonly entityType = ToolDefinition;

  private readonly mapperRef = inject(ToolDefinitionMapper);
  private readonly serviceRef = inject(ToolDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return ToolDefinitionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createToolDefinitionDescriptor();
  }
}
