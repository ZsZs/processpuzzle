import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityFacade, EntityServiceKind } from '@processpuzzle/base-entity';
import { FirestoreDoc } from './firestore-doc';
import { FirestoreDocMapper } from './firestore-doc.mapper';
import { FirestoreDocService } from './firestore-doc.service';
import { FirestoreDocStore } from './firestore-doc.store';
import { firestoreDocDescriptors } from './firestore-doc.descriptor';

@Injectable()
export class FirestoreDocFacade extends BaseEntityFacade<FirestoreDoc> {
  readonly entityType = FirestoreDoc;
  readonly entityName = 'Firestore Doc';
  readonly attrDescriptors = firestoreDocDescriptors;
  protected override readonly serviceKind: EntityServiceKind = 'firestore';

  private readonly mapperRef = inject(FirestoreDocMapper);
  private readonly serviceRef = inject(FirestoreDocService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return FirestoreDocStore;
  }
}
