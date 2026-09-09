import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { ModuleDefinition } from '../domain/module-definition';
import { createModuleDefinitionDescriptor } from '../domain/module-definition.descriptors';
import { ModuleDefinitionMapper } from '../domain/module-definition.mapper';
import { ModuleDefinitionService } from '../domain/module-definition.service';
import { ModuleDefinitionStore } from '../domain/module-definition.store';

@Injectable()
export class ModuleDefinitionFacade extends BaseEntityFacade<ModuleDefinition> {
  readonly entityType = ModuleDefinition;

  private readonly mapperRef = inject(ModuleDefinitionMapper);
  private readonly serviceRef = inject(ModuleDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return ModuleDefinitionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createModuleDefinitionDescriptor();
  }
}
