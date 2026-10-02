import { inject, Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { WIDGET_REGISTRY, WidgetRegistration } from '@processpuzzle/widgets';
import { WidgetInstance } from '../domain/app-definition';
import { createWidgetInstanceDescriptor, widgetTypeSelectables } from '../domain/widget-instance.descriptors';

/**
 * A widget sits under a region, a page or another widget, and one facade serves all three: which owner's
 * rows its store reads is decided by the route that is open, not by the facade.
 *
 * The `type` dropdown lists the registry this facade's injector sees — widgets registered only in a lazily
 * loaded route below it are not offered, the same limit the props control has.
 */
@Injectable()
export class AppWidgetFacade extends EmbeddedEntityFacade<WidgetInstance> {
  readonly entityType = WidgetInstance;
  private readonly widgetRegistry = inject(WIDGET_REGISTRY, { optional: true }) ?? new Map<string, WidgetRegistration>();

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWidgetInstanceDescriptor(widgetTypeSelectables(this.widgetRegistry));
  }
}
