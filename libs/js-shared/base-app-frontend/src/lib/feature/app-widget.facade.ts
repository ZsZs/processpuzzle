import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { WidgetRef } from '../domain/app-definition';
import { createWidgetRefDescriptor } from '../domain/widget-ref.descriptors';

/**
 * A widget sits under a region, a page or another widget, and one facade serves all three: which owner's
 * rows its store reads is decided by the route that is open, not by the facade.
 */
@Injectable()
export class AppWidgetFacade extends EmbeddedEntityFacade<WidgetRef> {
  readonly entityType = WidgetRef;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWidgetRefDescriptor();
  }
}
