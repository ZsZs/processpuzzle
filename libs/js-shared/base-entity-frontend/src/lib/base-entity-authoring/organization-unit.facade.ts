import { inject, Injectable, type Type } from '@angular/core';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityFacade } from '../base-entity-facade/base-entity-facade';
import { UNIT_CATALOG } from '../base-form/fundamental-types/unit-catalog.token';
import { OrganizationUnitRecord } from './organization-unit';
import { OrganizationUnitAuthoringService } from './organization-unit-authoring.service';
import { createOrganizationUnitDescriptor } from './organization-unit.descriptors';
import { OrganizationUnitMapper } from './organization-unit.mapper';
import { OrganizationUnitStore } from './organization-unit.store';

/**
 * The routable `Organization Unit`: the list and form of the units an organization adds. The mapper and the service are
 * injected rather than constructed, so the store's repository and this facade's are the same object.
 */
@Injectable()
export class OrganizationUnitFacade extends BaseEntityFacade<OrganizationUnitRecord> {
  readonly entityType = OrganizationUnitRecord;

  private readonly mapperRef = inject(OrganizationUnitMapper);
  private readonly serviceRef = inject(OrganizationUnitAuthoringService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return OrganizationUnitStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createOrganizationUnitDescriptor(this.injector.get(UNIT_CATALOG).kinds().map((kind) => kind.code));
  }
}
