import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { OrganizationRole } from '../domain/organization-user';
import { OrganizationRoleMapper } from '../domain/organization-user.mapper';
import { OrganizationRoleEntityService } from '../domain/organization-user.service';
import { OrganizationRoleStore } from '../domain/organization-role.store';
import { createOrganizationRoleDescriptor } from '../domain/organization-role.descriptors';

@Injectable()
export class OrganizationRoleFacade extends BaseEntityFacade<OrganizationRole> {
  readonly entityType = OrganizationRole;

  private readonly mapperRef = inject(OrganizationRoleMapper);
  private readonly serviceRef = inject(OrganizationRoleEntityService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return OrganizationRoleStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createOrganizationRoleDescriptor();
  }
}
