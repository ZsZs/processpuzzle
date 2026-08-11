import { Injectable } from '@angular/core';
import { FirestoreDoc } from './firestore-doc';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';

type FirestoreDocDto = Partial<FirestoreDoc>;

@Injectable({ providedIn: 'root' })
export class FirestoreDocMapper implements BaseEntityMapper<FirestoreDoc> {
  fromDto(dto: unknown): FirestoreDoc {
    const source = dto as FirestoreDocDto;
    return new FirestoreDoc(source.id, source.name, source.description);
  }

  toDto(entity: FirestoreDoc): unknown {
    return entity;
  }
}
