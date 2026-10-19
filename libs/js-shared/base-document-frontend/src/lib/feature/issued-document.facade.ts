import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { IssuedDocument } from '../domain/issued-document/issued-document';
import { createIssuedDocumentDescriptor } from '../domain/issued-document/issued-document.descriptors';
import { IssuedDocumentMapper } from '../domain/issued-document/issued-document.mapper';
import { IssuedDocumentService } from '../domain/issued-document/issued-document.service';
import { IssuedDocumentStore } from '../domain/issued-document/issued-document.store';

@Injectable()
export class IssuedDocumentFacade extends BaseEntityFacade<IssuedDocument> {
  readonly entityType = IssuedDocument;

  private readonly mapperRef = inject(IssuedDocumentMapper);
  private readonly serviceRef = inject(IssuedDocumentService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return IssuedDocumentStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createIssuedDocumentDescriptor();
  }
}
