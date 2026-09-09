import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { RegionDefinition } from '../domain/app-definition';
import { createRegionDefinitionDescriptor } from '../domain/region-definition.descriptors';

/**
 * A region has a facade like any other entity — that is what gives it a store, and so a working list and
 * form. Its repository reads and writes the `App Definition` document instead of an endpoint of its own,
 * because the contract nests `regions` inside that document.
 */
@Injectable()
export class AppRegionFacade extends EmbeddedEntityFacade<RegionDefinition> {
  readonly entityType = RegionDefinition;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createRegionDefinitionDescriptor();
  }
}
