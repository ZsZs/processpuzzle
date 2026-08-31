import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { RoleDefinition } from '../../domain/definition/role-definition';
import { createRoleDefinitionDescriptor } from '../../domain/definition/role-definition.descriptors';
import { RoleDefinitionMapper } from '../../domain/definition/role-definition.mapper';
import { RoleDefinitionService } from '../../domain/definition/role-definition.service';
import { RoleDefinitionStore } from '../../domain/definition/role-definition.store';
import { ROLE_MODELER_TAB } from './role-modeler-tab';

@Injectable()
export class WorkflowRoleDefinitionFacade extends BaseEntityFacade<RoleDefinition> {
  readonly entityType = RoleDefinition;

  private readonly mapperRef = inject(RoleDefinitionMapper);
  private readonly serviceRef = inject(RoleDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return RoleDefinitionStore;
  }

  /**
   * The generated descriptor, plus the modeler tab. Contributed here rather than in the descriptor factory
   * because this is the one place `BaseEntityTabsComponent` reads tabs from — and because the factory is
   * pure domain metadata, which a component reference is not.
   */
  protected override createDescriptor(): BaseEntityDescriptor {
    const descriptor = createRoleDefinitionDescriptor();
    descriptor.extraTabs = [ROLE_MODELER_TAB];
    return descriptor;
  }
}
