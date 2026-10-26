import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { OrganizationRole } from './organization-user';
import { OrganizationRoleEntityService } from './organization-user.service';

export const OrganizationRoleStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<OrganizationRole>(OrganizationRole, () => inject(OrganizationRoleEntityService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('OrganizationRole'),
);
