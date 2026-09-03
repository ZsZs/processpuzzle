import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { Organization } from './organization';
import { OrganizationService } from './organization.service';

export const OrganizationStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<Organization>(Organization, () => inject(OrganizationService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('Organization'),
);
