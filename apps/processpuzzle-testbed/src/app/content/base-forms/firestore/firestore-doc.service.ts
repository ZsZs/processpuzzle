import { Injectable } from '@angular/core';
import { BaseEntityFirestoreService } from '@processpuzzle/base-entity';
import { FirestoreDoc } from './firestore-doc';
import { FirestoreDocMapper } from './firestore-doc.mapper';

@Injectable({ providedIn: 'root' })
export class FirestoreDocService extends BaseEntityFirestoreService<FirestoreDoc> {
  constructor(protected override entityMapper: FirestoreDocMapper) {
    super(entityMapper, 'firestore-doc');
  }
}
