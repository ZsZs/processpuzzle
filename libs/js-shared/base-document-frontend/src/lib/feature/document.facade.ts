import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { Document } from '../domain/base-document';
import { createDocumentDescriptor } from '../domain/base-document.descriptors';
import { BaseDocumentMapper } from '../domain/base-document.mapper';
import { BaseDocumentService } from '../domain/base-document.service';
import { BaseDocumentStore } from '../domain/base-document.store';

/**
 * The routable entity of this library, so `Document` resolves out of `BASE_ENTITY_FACADE_REGISTRY` the way
 * every other entity does — which is what the generic list and form reach it through.
 *
 * The store class is the root-provided {@link BaseDocumentStore} rather than one minted per facade, so the
 * instance {@link BaseDocumentContainerComponent} injects to feed the content editor and the one the
 * generic screens read their rows from are the same. Likewise the mapper and the service are the
 * root-provided ones: {@link BaseDocumentService} redirects the form's save to `PUT /documents/{id}/
 * properties`, and a service built by the base class instead would put the whole entity back and wipe the
 * blocks.
 */
@Injectable()
export class DocumentFacade extends BaseEntityFacade<Document> {
  readonly entityType = Document;

  private readonly mapperRef = inject(BaseDocumentMapper);
  private readonly serviceRef = inject(BaseDocumentService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return BaseDocumentStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createDocumentDescriptor();
  }
}
