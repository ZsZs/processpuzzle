import { signalStore } from '@ngrx/signals';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { FirestoreDoc } from './firestore-doc';
import { FirestoreDocService } from './firestore-doc.service';

export const FirestoreDocStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<FirestoreDoc>(FirestoreDoc, FirestoreDocService),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
