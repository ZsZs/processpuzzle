import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { WidgetInstance } from '../domain/app-definition';
import { createWidgetInstanceDescriptor } from '../domain/widget-instance.descriptors';

/**
 * A widget sits under a region, a page or another widget, and one facade serves all three: which owner's
 * rows its store reads is decided by the route that is open, not by the facade.
 */
@Injectable()
export class AppWidgetFacade extends EmbeddedEntityFacade<WidgetInstance> {
  readonly entityType = WidgetInstance;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWidgetInstanceDescriptor();
  }
}
