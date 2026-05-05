import { signalStore } from '@ngrx/signals';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { TrunkDataService } from './trunk-data.service';
import { TrunkData } from './trunk-data';

export const TrunkDataStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TrunkData>(TrunkData, TrunkDataService),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
