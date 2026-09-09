import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { OrganizationUser } from '../domain/organization-user';
import { OrganizationUserMapper } from '../domain/organization-user.mapper';
import { OrganizationUserService } from '../domain/organization-user.service';
import { OrganizationUserStore } from '../domain/organization-user.store';
import { createOrganizationUserDescriptor } from '../domain/organization-user.descriptors';

@Injectable()
export class OrganizationUserFacade extends BaseEntityFacade<OrganizationUser> {
  readonly entityType = OrganizationUser;

  private readonly mapperRef = inject(OrganizationUserMapper);
  private readonly serviceRef = inject(OrganizationUserService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return OrganizationUserStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createOrganizationUserDescriptor();
  }
}
