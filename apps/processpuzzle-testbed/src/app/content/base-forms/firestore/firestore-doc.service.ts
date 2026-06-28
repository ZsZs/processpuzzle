import { Injectable } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityFirestoreService } from '@processpuzzle/base-entity';
import { FirestoreDoc } from './firestore-doc';
import { FirestoreDocMapper } from './firestore-doc.mapper';

@Injectable({ providedIn: 'root' })
export class FirestoreDocService extends BaseEntityFirestoreService<FirestoreDoc> {
  constructor(protected override entityMapper: FirestoreDocMapper) {
    super(entityMapper, 'firestore-doc');
  }
}
