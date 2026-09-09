import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { WidgetDefinition } from './widget-definition';
import { createWidgetDefinitionDescriptor } from './widget-definition.descriptors';
import { WidgetDefinitionMapper } from './widget-definition.mapper';
import { WidgetDefinitionService } from './widget-definition.service';
import { WidgetDefinitionStore } from './widget-definition.store';

/**
 * The routable facade of this library: the root-provided mapper, service and store are reused rather than
 * created, so the screens the route renders and the container's Publish action work on one store.
 */
@Injectable()
export class WidgetDefinitionFacade extends BaseEntityFacade<WidgetDefinition> {
  readonly entityType = WidgetDefinition;

  private readonly mapperRef = inject(WidgetDefinitionMapper);
  private readonly serviceRef = inject(WidgetDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return WidgetDefinitionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWidgetDefinitionDescriptor();
  }
}
