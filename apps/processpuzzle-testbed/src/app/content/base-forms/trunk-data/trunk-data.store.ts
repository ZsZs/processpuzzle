import { signalStore } from '@ngrx/signals';
import { inject } from '@angular/core';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { TrunkDataService } from './trunk-data.service';
import { TrunkData } from './trunk-data';

export const TrunkDataStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TrunkData>(TrunkData, () => inject(TrunkDataService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
