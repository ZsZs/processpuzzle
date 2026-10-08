import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { EventDefinition } from '../domain/event-definition';
import { createEventDefinitionDescriptor } from '../domain/event-definition.descriptors';
import { EventDefinitionMapper } from '../domain/event-definition.mapper';
import { EventDefinitionService } from '../domain/event-definition.service';
import { EventDefinitionStore } from '../domain/event-definition.store';

@Injectable()
export class EventDefinitionFacade extends BaseEntityFacade<EventDefinition> {
  readonly entityType = EventDefinition;

  private readonly mapperRef = inject(EventDefinitionMapper);
  private readonly serviceRef = inject(EventDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return EventDefinitionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createEventDefinitionDescriptor();
  }
}
