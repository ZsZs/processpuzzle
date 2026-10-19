import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { IssuedDocument } from './issued-document';
import { IssuedDocumentMapper } from './issued-document.mapper';

/** `GET /organizations/{orgKey}/issued-documents`, newest first. The list is paged by the server and read-only. */
@Injectable({ providedIn: 'root' })
export class IssuedDocumentService extends BaseEntityRestService<IssuedDocument> {
  constructor(protected override entityMapper: IssuedDocumentMapper) {
    super(entityMapper, 'DOCUMENT_SERVICE_ROOT', 'issued-documents');
  }
}
