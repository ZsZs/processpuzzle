import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { PageDefinition } from '../domain/app-definition';
import { createPageDefinitionDescriptor } from '../domain/page-definition.descriptors';

/**
 * `GET .../app-definitions/{appId}/pages/{pageId}` is a read-only projection for the run-time shell, not
 * a write endpoint — a page is authored inside the `App Definition` document, so its store goes through
 * that document like every other embedded child of the graph.
 */
@Injectable()
export class AppPageFacade extends EmbeddedEntityFacade<PageDefinition> {
  readonly entityType = PageDefinition;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createPageDefinitionDescriptor();
  }
}
