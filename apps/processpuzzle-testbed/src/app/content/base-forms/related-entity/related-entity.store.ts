import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { inject } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { RelatedEntityService } from './related-entity.service';
import { RelatedEntity } from './related-entity';

export const RelatedEntityStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<RelatedEntity>(RelatedEntity, () => inject(RelatedEntityService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('RelatedEntity'),
);
