import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { OrganizationUser } from './organization-user';
import { OrganizationUserService } from './organization-user.service';

export const OrganizationUserStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<OrganizationUser>(OrganizationUser, () => inject(OrganizationUserService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('OrganizationUser'),
);
