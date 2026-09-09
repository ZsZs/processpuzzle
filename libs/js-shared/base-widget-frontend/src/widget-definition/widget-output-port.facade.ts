import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { OutputPort } from './widget-definition';
import { createWidgetOutputPortDescriptor } from './widget-port.descriptors';

/** The output side of {@link WidgetInputPortFacade}, embedded in the definition payload for the same reason. */
@Injectable()
export class WidgetOutputPortFacade extends EmbeddedEntityFacade<OutputPort> {
  readonly entityType = OutputPort;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWidgetOutputPortDescriptor();
  }
}
