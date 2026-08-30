import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { RoleDefinition } from '../../domain/definition/role-definition';
import { createRoleDefinitionDescriptor } from '../../domain/definition/role-definition.descriptors';
import { RoleDefinitionMapper } from '../../domain/definition/role-definition.mapper';
import { RoleDefinitionService } from '../../domain/definition/role-definition.service';
import { RoleDefinitionStore } from '../../domain/definition/role-definition.store';

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

  protected override createDescriptor(): BaseEntityDescriptor {
    return createRoleDefinitionDescriptor();
  }
}
