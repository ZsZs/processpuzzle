import { inject, Injectable, Type } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityDescriptor, BaseEntityFacade, EntityServiceKind } from '@processpuzzle/base-entity';
import { FirestoreDoc } from './firestore-doc';
import { FirestoreDocMapper } from './firestore-doc.mapper';
import { FirestoreDocService } from './firestore-doc.service';
import { FirestoreDocStore } from './firestore-doc.store';
import { createFirestoreDocDescriptor } from './firestore-doc.descriptor';

@Injectable()
export class FirestoreDocFacade extends BaseEntityFacade<FirestoreDoc> {
  readonly entityType = FirestoreDoc;
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

  protected override createDescriptor(): BaseEntityDescriptor {
    return createFirestoreDocDescriptor();
  }
}
