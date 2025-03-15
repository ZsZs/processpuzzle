import { signalStore } from '@ngrx/signals';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore, BaseFormNavigatorStore } from '@processpuzzle/base-entity';
import { FirestoreDoc } from './firestore-doc';
import { FirestoreDocService } from './firestore-doc.service';

export const FirestoreDocStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<FirestoreDoc>(FirestoreDoc, FirestoreDocService),
  BaseFormNavigatorStore('FirestoreDoc'),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
