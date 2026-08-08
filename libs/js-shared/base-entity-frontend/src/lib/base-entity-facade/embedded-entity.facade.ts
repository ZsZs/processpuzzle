import { inject, Injectable, runInInjectionContext, Type } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntity } from '../base-entity/base-entity';
import { entityNameFromType } from '../base-entity/base-entity-utility';
import { EmbeddedRow, rowId } from '../base-entity-embedded/embedded-aggregate';
import { EmbeddedAggregateAccessor } from '../base-entity-embedded/embedded-aggregate.accessor';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { EmbeddedEntityService } from '../base-entity-service/embedded-entity.service';
import { BaseEntityStore } from '../base-entity-store/base-entity.store';
import { BaseEntityTabsStore } from '../base-tabs/base-entity-tabs.store';
import { BaseEntityContainerStore } from '../base-entity-container.store';
import { BaseEntityFacade } from './base-entity-facade';

/**
 * Facade of an entity whose payload lives inside another entity's document. Everything but the repository
 * and the store's notion of a key is inherited: the descriptor, the tabs, the container state — so an
 * embedded entity registers in `BASE_ENTITY_FACADE_REGISTRY` alongside the routable ones and its list and
 * form are the stock components.
 *
 * The descriptor still has to declare `componentParent` and `isEmbedded`, and the containing entity still
 * has to reference it with `FormControlType.EMBEDDED_COMPONENTS`; the form control checks both agree.
 */
@Injectable()
export abstract class EmbeddedEntityFacade<Entity extends BaseEntity> extends BaseEntityFacade<Entity> {
  private _accessor?: EmbeddedAggregateAccessor;

  /** Takes no mapper: an embedded row is carried, and mapped, by the containing entity's payload. */
  protected override createService(): BaseEntityService<Entity> {
    return new EmbeddedEntityService<Entity>(this.entityName, this.accessor);
  }

  /**
   * The generic store, keyed by whatever identifies an embedded row rather than by `id`.
   *
   * An embedded child is not guaranteed to have one — `App Region` is identified by `type`, the contract
   * giving a region no id — and the store is what the details form reads its row from (`loadById`) and
   * what a delete removes it from. Keyed by `id` alone those would silently miss every such row, or, since
   * `undefined === undefined`, hit the wrong one.
   *
   * The field is read per call rather than captured, for the same reason the accessor resolves per call:
   * one store serves every owner of this child type, and which attribute's rows it stands for is decided
   * by the route that is open.
   */
  protected override createStoreClass(): Type<unknown> {
    return signalStore(
      { providedIn: 'root' },
      BaseEntityStore<Entity>(this.entityType, () => this.service, this.rowKeyOf),
      BaseEntityTabsStore(),
      BaseEntityContainerStore(),
      withDevtools(entityNameFromType(this.entityType)),
    );
  }

  private readonly rowKeyOf = (entity: Entity): string | undefined => {
    const referenceIdField = this.accessor.resolve(this.entityName)?.context.referenceIdField;
    return rowId(entity as EmbeddedRow, referenceIdField) || undefined;
  };

  private get accessor(): EmbeddedAggregateAccessor {
    this._accessor ??= runInInjectionContext(this.injector, () => inject(EmbeddedAggregateAccessor));
    return this._accessor;
  }
}
