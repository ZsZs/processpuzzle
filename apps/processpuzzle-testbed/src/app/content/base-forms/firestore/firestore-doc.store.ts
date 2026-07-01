import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { inject } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { FirestoreDoc } from './firestore-doc';
import { FirestoreDocService } from './firestore-doc.service';

export const FirestoreDocStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<FirestoreDoc>(FirestoreDoc, () => inject(FirestoreDocService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('FirestoreDoc'),
);
