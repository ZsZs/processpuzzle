import { Injectable } from '@angular/core';
import { FirestoreDoc } from './firestore-doc';
import { BaseEntityMapper } from '@processpuzzle/base-entity';

@Injectable({ providedIn: 'root' })
export class FirestoreDocMapper implements BaseEntityMapper<FirestoreDoc> {
  fromDto(dto: any): FirestoreDoc {
    return new FirestoreDoc(dto.id, dto.name, dto.description);
  }

  toDto(entity: FirestoreDoc): any {
    return entity;
  }
}
