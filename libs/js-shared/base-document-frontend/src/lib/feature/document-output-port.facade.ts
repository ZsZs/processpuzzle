import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { DocumentOutputPort } from '../domain/base-document';
import { createDocumentOutputPortDescriptor } from '../domain/document-port.descriptors';

/** The output side of {@link DocumentInputPortFacade}, embedded in the `Document` payload for the same reason. */
@Injectable()
export class DocumentOutputPortFacade extends EmbeddedEntityFacade<DocumentOutputPort> {
  readonly entityType = DocumentOutputPort;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createDocumentOutputPortDescriptor();
  }
}
