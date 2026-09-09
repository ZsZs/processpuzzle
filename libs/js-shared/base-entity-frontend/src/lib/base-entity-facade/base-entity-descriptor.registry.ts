import { inject, Injectable, Injector } from '@angular/core';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { ResolvedEntityCache } from '../base-entity-definition/resolved-entity.cache';
import { BASE_ENTITY_FACADE_REGISTRY } from './base-entity-facade-registry';

/**
 * Resolves an entity's descriptor and store **by name**, whichever world the entity comes from: a
 * compile-time `BaseEntityFacade` registered in `BASE_ENTITY_FACADE_REGISTRY`, or a `BaseEntityDefinition`
 * synthesized at run-time by `DynamicEntityRegistry` and published into {@link ResolvedEntityCache}.
 *
 * Being the single lookup surface is what makes the metadata-defined case cost the rest of the library
 * nothing. `EmbeddedAggregateAccessor` asks here for the root store of an aggregate and for the descriptor
 * of every level below it; `resolveEmbeddedRouteContext` walks the containment graph through the
 * descriptors it gets back; the embedded form controls read their child's label the same way. None of them
 * changed, and none of them can tell the difference.
 *
 * Compile-time first. A host application that registered a facade did so on purpose — it may carry extra
 * tabs, a hand-tuned layout or a Firestore repository — and a definition of the same name does not
 * override that decision. The dynamic side is a fallback, in both senses.
 *
 * The synthesized side is read out of a cache rather than from the registry that builds it, so that this
 * class — injected by half the library — does not depend on the facades, and through them on every
 * repository implementation there is. See {@link ResolvedEntityCache}.
 */
@Injectable({ providedIn: 'root' })
export class BaseEntityDescriptorRegistry {
  private readonly registry = inject(BASE_ENTITY_FACADE_REGISTRY);
  private readonly resolvedEntities = inject(ResolvedEntityCache);
  private readonly injector = inject(Injector);

  getDescriptor(entityName: string | undefined): BaseEntityDescriptor | undefined {
    return this.resolveFacade(entityName)?.descriptor ?? this.resolvedEntities.descriptorOf(entityName);
  }

  /**
   * An embedded entity has a store too — see `EmbeddedEntityFacade`. It reads and writes the containing
   * entity's document rather than an endpoint of its own, which is invisible from here.
   */
  getStore<Store = unknown>(entityName: string | undefined): Store | undefined {
    return (this.resolveFacade(entityName)?.store as Store | undefined) ?? (this.resolvedEntities.storeOf(entityName) as Store | undefined);
  }

  private resolveFacade(entityName: string | undefined) {
    if (!entityName) return undefined;
    const facadeToken = this.registry[entityName];
    if (!facadeToken) return undefined;
    return this.injector.get(facadeToken, null, { optional: true }) ?? undefined;
  }
}
