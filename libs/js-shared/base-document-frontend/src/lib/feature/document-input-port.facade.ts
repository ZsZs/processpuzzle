import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { DocumentInputPort } from '../domain/base-document';
import { createDocumentInputPortDescriptor } from '../domain/document-port.descriptors';

/**
 * An input port has a facade like any other entity — that is what gives it a store, and so a working list
 * and form. Its repository reads and writes the `Document` payload instead of an endpoint of its own,
 * because the contract nests `inputPorts` inside that document.
 */
@Injectable()
export class DocumentInputPortFacade extends EmbeddedEntityFacade<DocumentInputPort> {
  readonly entityType = DocumentInputPort;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createDocumentInputPortDescriptor();
  }
}
