import { Injectable } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { EmbeddedComponent } from './embedded-component';
import { createEmbeddedComponentDescriptor } from './embedded-component.descriptors';

/**
 * An embedded entity has a facade like any other — that is what gives it a store, and so a working list and
 * form. Its repository just reads and writes `Test Entity`'s document instead of an endpoint of its own.
 */
@Injectable()
export class EmbeddedComponentFacade extends EmbeddedEntityFacade<EmbeddedComponent> {
  readonly entityType = EmbeddedComponent;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createEmbeddedComponentDescriptor();
  }
}
