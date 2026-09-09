import { inject, Injectable, type Type } from '@angular/core';
import { EntityAttributeDefinition, EntityDefinition } from '../base-entity-definition/entity-definition';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityFacade } from '../base-entity-facade/base-entity-facade';
import { EmbeddedEntityFacade } from '../base-entity-facade/embedded-entity.facade';
import { createEntityAttributeDescriptor } from './entity-attribute.descriptors';
import { createEntityDefinitionDescriptor } from './entity-definition.descriptors';
import { EntityDefinitionAuthoringService } from './entity-definition-authoring.service';
import { EntityDefinitionMapper } from './entity-definition.mapper';
import { EntityDefinitionStore } from './entity-definition.store';

/**
 * The routable `Entity Definition`. Everything the generated screens need comes off this facade, which is
 * what `ACTIVE_ENTITY_FACADE` hands to the container, the list and the form.
 *
 * The mapper and the service are injected rather than constructed, so both stay `providedIn: 'root'`
 * singletons — the store's repository and this facade's have to be the same object, or a save would not be
 * visible to the rows the list already holds.
 */
@Injectable()
export class EntityDefinitionFacade extends BaseEntityFacade<EntityDefinition> {
  readonly entityType = EntityDefinition;

  private readonly mapperRef = inject(EntityDefinitionMapper);
  private readonly serviceRef = inject(EntityDefinitionAuthoringService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return EntityDefinitionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createEntityDefinitionDescriptor();
  }
}

/**
 * The one embedded level of a definition: its attributes, carried inside the definition document.
 *
 * A facade like any other — that is what gives it a store, and so a working list and form — with only its
 * repository differing: `EmbeddedEntityService` reads and writes the `Entity Definition` being edited
 * rather than an endpoint of its own. It takes no mapper for the same reason; the rows are mapped by
 * `EntityDefinitionMapper` as part of the owner's payload.
 */
@Injectable()
export class EntityAttributeFacade extends EmbeddedEntityFacade<EntityAttributeDefinition> {
  readonly entityType = EntityAttributeDefinition;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createEntityAttributeDescriptor();
  }
}
