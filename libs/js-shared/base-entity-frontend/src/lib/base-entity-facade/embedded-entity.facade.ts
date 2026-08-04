import { inject, Injectable, runInInjectionContext } from '@angular/core';
import { BaseEntity } from '../base-entity/base-entity';
import { EmbeddedAggregateAccessor } from '../base-entity-embedded/embedded-aggregate.accessor';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { EmbeddedEntityService } from '../base-entity-service/embedded-entity.service';
import { BaseEntityFacade } from './base-entity-facade';

/**
 * Facade of an entity whose payload lives inside another entity's document. Everything but the repository is
 * inherited: the store composition, the descriptor, the tabs — so an embedded entity registers in
 * `BASE_ENTITY_FACADE_REGISTRY` alongside the routable ones and its list and form are the stock components.
 *
 * The descriptor still has to declare `componentParent` and `isEmbedded`, and the containing entity still has
 * to reference it with `FormControlType.EMBEDDED_COMPONENTS`; the form control checks both agree.
 */
@Injectable()
export abstract class EmbeddedEntityFacade<Entity extends BaseEntity> extends BaseEntityFacade<Entity> {
  /** Takes no mapper: an embedded row is carried, and mapped, by the containing entity's payload. */
  protected override createService(): BaseEntityService<Entity> {
    return runInInjectionContext(this.injector, () => new EmbeddedEntityService<Entity>(this.entityName, inject(EmbeddedAggregateAccessor)));
  }
}
