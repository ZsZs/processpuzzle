import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { InputPort } from './widget-definition';
import { createWidgetInputPortDescriptor } from './widget-port.descriptors';

/**
 * An input port has a facade like any other entity — that is what gives it a store, and so a working list
 * and form. Its repository reads and writes the `WidgetDefinition` payload instead of an endpoint of its
 * own, because the contract nests `inputPorts` inside that definition.
 */
@Injectable()
export class WidgetInputPortFacade extends EmbeddedEntityFacade<InputPort> {
  readonly entityType = InputPort;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createWidgetInputPortDescriptor();
  }
}
