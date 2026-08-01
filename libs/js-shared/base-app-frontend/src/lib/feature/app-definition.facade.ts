import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { AppDefinition } from '../domain/app-definition';
import { AppDefinitionMapper } from '../domain/app-definition.mapper';
import { AppDefinitionService } from '../domain/app-definition.service';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { createAppDefinitionDescriptor } from '../domain/app-definition.descriptors';

@Injectable()
export class AppDefinitionFacade extends BaseEntityFacade<AppDefinition> {
  readonly entityType = AppDefinition;

  private readonly mapperRef = inject(AppDefinitionMapper);
  private readonly serviceRef = inject(AppDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return AppDefinitionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createAppDefinitionDescriptor();
  }
}
