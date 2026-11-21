import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore } from '../base-entity-container.store';
import { BaseEntityStore } from '../base-entity-store/base-entity.store';
import { BaseEntityTabsStore } from '../base-tabs/base-entity-tabs.store';
import { OrganizationUnitRecord } from './organization-unit';
import { OrganizationUnitAuthoringService } from './organization-unit-authoring.service';

/** The stock CRUD store, `providedIn: 'root'` because it outlives the route, like the definition store beside it. */
export const OrganizationUnitStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<OrganizationUnitRecord>(OrganizationUnitRecord, () => inject(OrganizationUnitAuthoringService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('OrganizationUnit'),
);
