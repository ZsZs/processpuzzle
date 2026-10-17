import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { EventParameter } from '../domain/event-definition';
import { createEventParameterDescriptor } from '../domain/event-parameter.descriptors';
import { EventDefinitionOptions } from './event-definition.options';

/**
 * The embedded level of an event definition: its parameters. A facade like any other entity's — that is what
 * gives the rows a store, and so a working list and form — whose repository reads and writes the
 * definition's document rather than an endpoint of its own.
 */
@Injectable()
export class EventParameterFacade extends EmbeddedEntityFacade<EventParameter> {
  readonly entityType = EventParameter;

  protected override createDescriptor(): BaseEntityDescriptor {
    const options = () => this.injector.get(EventDefinitionOptions);
    return createEventParameterDescriptor({
      subjectAttributes: () => options().subjectAttributes(),
      referenceTypes: () => options().referenceTypes(),
    });
  }
}
