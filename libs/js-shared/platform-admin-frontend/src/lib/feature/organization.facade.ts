import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { Organization } from '../domain/organization';
import { OrganizationMapper } from '../domain/organization.mapper';
import { OrganizationService } from '../domain/organization.service';
import { OrganizationStore } from '../domain/organization.store';
import { createOrganizationDescriptor } from '../domain/organization.descriptors';

@Injectable()
export class OrganizationFacade extends BaseEntityFacade<Organization> {
  readonly entityType = Organization;

  private readonly mapperRef = inject(OrganizationMapper);
  private readonly serviceRef = inject(OrganizationService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return OrganizationStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createOrganizationDescriptor();
  }
}
