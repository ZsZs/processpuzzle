import { Component, inject, OnDestroy } from '@angular/core';
import { BaseEntityContainerComponent } from '../base-entity-container.component';
import { DynamicEntityRegistry } from '../base-entity-definition/dynamic-entity.registry';

/**
 * Hosts the generic container for `Entity Definition`, and invalidates the two run-time caches when the
 * author leaves the section.
 *
 * The container takes no inputs: `BaseEntityContainerComponent` resolves its descriptor and its store from
 * `ACTIVE_ENTITY_FACADE`, which `BASE_ENTITY_AUTHORING_ROUTES` binds to `EntityDefinitionFacade` — the same
 * token every level below it resolves through, so the definition and its attributes are reached the same
 * way. A component of this library's own is nevertheless what the route mounts, and the cache reset is the
 * whole reason.
 *
 * **Why the reset.** `EntityDefinitionRegistry` memoizes the tenant's definitions for the session, and
 * `DynamicEntityRegistry` memoizes the descriptors and facades synthesized from them. That is right for a
 * run-time shell, where metadata does not change under a running application — and wrong in the designer,
 * where changing it is the point: a definition edited here decides what the Preview tab and every
 * `EntityScreenResolver`-mounted screen should render next, and nothing else would ever invalidate a
 * session-long cache. Both registries name this caller in their own `reset()` docs.
 *
 * Only `DynamicEntityRegistry` is asked, because its `reset()` cascades to `EntityDefinitionRegistry` —
 * discarding the descriptors without discarding the definitions they were built from would rebuild the same
 * descriptors.
 *
 * **Why on destroy** rather than after each save: leaving the section is the last moment before a stale
 * descriptor could be rendered, it happens once, and it cannot be mistaken for a change when the author only
 * looked. Refetching after a visit that edited nothing costs one request; the alternative — resetting on
 * every mutation of the store's row set — also fires on a page change and a filter.
 */
@Component({
  selector: 'pp-entity-definition-container',
  standalone: true,
  imports: [BaseEntityContainerComponent],
  template: `<base-entity-container></base-entity-container>`,
})
export class EntityDefinitionContainerComponent implements OnDestroy {
  private readonly dynamicEntities = inject(DynamicEntityRegistry);

  ngOnDestroy(): void {
    this.dynamicEntities.reset();
  }
}
