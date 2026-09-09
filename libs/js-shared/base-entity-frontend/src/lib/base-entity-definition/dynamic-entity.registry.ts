import { inject, Injectable, InjectionToken, Injector, ProviderToken, runInInjectionContext } from '@angular/core';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityFacade } from '../base-entity-facade/base-entity-facade';
import { descriptorOf } from './dynamic-entity.descriptor';
import { DynamicEmbeddedEntityFacade, DynamicEntityFacade } from './dynamic-entity.facade';
import { EntityDefinition } from './entity-definition';
import { EntityDefinitionRegistry } from './entity-definition.registry';
import { ResolvedEntityCache } from './resolved-entity.cache';

/** One metadata-defined entity, ready to render. */
export interface ResolvedDynamicEntity {
  entityName: string;
  descriptor: BaseEntityDescriptor;
  facade: BaseEntityFacade<BaseEntity>;
  /**
   * Token the facade answers to. `EmbeddedChildRoute.facade` and `ACTIVE_ENTITY_FACADE` are
   * `ProviderToken`s, so a facade that was never declared as a provider still needs one — see
   * {@link DynamicEntityRegistry.tokenFor}.
   */
  facadeToken: ProviderToken<BaseEntityFacade<BaseEntity>>;
}

/**
 * Builds and holds the facades of metadata-defined entities — the counterpart of
 * `BASE_ENTITY_FACADE_REGISTRY` for entity types that exist only as `BaseEntityDefinition` rows.
 *
 * `resolve` is async because the definitions have to be fetched; everything after that is synchronous, and
 * deliberately so. Once an entity is resolved, `descriptorOf` and `storeOf` answer immediately, which is
 * what lets `BaseEntityDescriptorRegistry` — and through it `EmbeddedAggregateAccessor`, the embedded route
 * context and every embedded form control — keep the synchronous signature they already have.
 *
 * **Resolving an entity resolves its whole aggregate.** An `EMBEDDED_COMPONENTS` attribute names a child
 * definition, and that child needs a descriptor and a store of its own before its rows can be listed on the
 * parent's form. Doing it here, in one pass, is what makes the parent's form work on the first render
 * rather than after a second navigation.
 *
 * Everything is created inside `runInInjectionContext`: facades inject `Injector`, the REST service injects
 * `HttpClient`, and `resolve` is called from a route builder that has already awaited — at which point the
 * ambient injection context is gone.
 */
@Injectable({ providedIn: 'root' })
export class DynamicEntityRegistry {
  private readonly definitions = inject(EntityDefinitionRegistry);
  private readonly resolvedEntities = inject(ResolvedEntityCache);
  private readonly injector = inject(Injector);
  private readonly resolved = new Map<string, ResolvedDynamicEntity>();
  private readonly tokens = new Map<string, ProviderToken<BaseEntityFacade<BaseEntity>>>();

  /**
   * The entity named `entityName`, or `undefined` when this tenant has no such definition — which is an
   * ordinary answer, not an error: an `AppDefinition` may name an entity that has been renamed or deleted,
   * and the caller renders "no entity type registered" for it.
   */
  async resolve(entityName: string | undefined): Promise<ResolvedDynamicEntity | undefined> {
    if (!entityName) return undefined;
    const known = this.resolved.get(entityName);
    if (known) return known;

    const definition = await this.definitions.byName(entityName);
    if (!definition) return undefined;

    // The whole map, so the code -> name translation the descriptor needs and the embedded children below
    // are looked up without another await inside the synchronous build.
    const byCode = await this.definitions.load();
    return this.build(definition, byCode, new Set<string>());
  }

  /** The descriptor of an already-resolved entity. Synchronous; `undefined` before {@link resolve}. */
  descriptorOf(entityName: string | undefined): BaseEntityDescriptor | undefined {
    return entityName ? this.resolved.get(entityName)?.descriptor : undefined;
  }

  /** The store of an already-resolved entity, bound into its descriptor by the facade. */
  storeOf(entityName: string | undefined): unknown {
    return entityName ? this.resolved.get(entityName)?.facade.store : undefined;
  }

  /**
   * Token the facade of an already-resolved entity answers to, for a caller that has to *provide* it — a
   * route branch binding `ACTIVE_ENTITY_FACADE`. `undefined` before {@link resolve}, so that nothing can
   * hand the router a token whose factory would throw on the first navigation into it.
   */
  facadeTokenOf(entityName: string | undefined): ProviderToken<BaseEntityFacade<BaseEntity>> | undefined {
    return entityName ? this.resolved.get(entityName)?.facadeToken : undefined;
  }

  /**
   * Discards every built facade, so the next `resolve` rebuilds from freshly fetched definitions.
   *
   * For the designer: an edited definition means a different descriptor. The tokens are kept — an
   * `InjectionToken`'s root factory is called once and its result cached by the injector, so a token
   * handed out earlier has to keep answering; it reads through {@link resolved}, which is why replacing
   * the entry is enough.
   */
  reset(): void {
    this.resolved.clear();
    this.resolvedEntities.clear();
    this.definitions.reset();
  }

  /**
   * The facade of `entityName` as a token.
   *
   * One token per entity name, created once and kept: `EmbeddedChildRoute.facade` is a `ProviderToken` that
   * `baseEntityRoutes` turns into `{ provide: ACTIVE_ENTITY_FACADE, useExisting: token }`, and a second
   * token for the same entity would give the same routes two facades and therefore two stores.
   *
   * The factory reads through {@link resolved} rather than closing over the facade, so that a `reset()`
   * followed by a re-`resolve` is visible through a token the router still holds.
   */
  private tokenFor(entityName: string): ProviderToken<BaseEntityFacade<BaseEntity>> {
    const known = this.tokens.get(entityName);
    if (known) return known;

    const token = new InjectionToken<BaseEntityFacade<BaseEntity>>(`DYNAMIC_ENTITY_FACADE(${entityName})`, {
      providedIn: 'root',
      factory: () => {
        const facade = this.resolved.get(entityName)?.facade;
        if (!facade) throw new Error(`'${entityName}' has no resolved definition; DynamicEntityRegistry.resolve() has to run before its routes are entered.`);
        return facade;
      },
    });
    this.tokens.set(entityName, token);
    return token;
  }

  /**
   * Synthesizes one definition's descriptor and facade, then recurses into the children its
   * `EMBEDDED_COMPONENTS` attributes name.
   *
   * `building` guards the recursion: an embedded child may nest inside itself (base-app's `App Nav Item`
   * does), which is a finite definition graph but an infinite tree. The child is registered before the
   * recursion so a self-reference finds the entry that is being built rather than starting a second one.
   */
  private build(definition: EntityDefinition, byCode: ReadonlyMap<string, EntityDefinition>, building: Set<string>): ResolvedDynamicEntity {
    const known = this.resolved.get(definition.name);
    if (known) return known;

    const descriptor = descriptorOf(definition, (code) => byCode.get(code));
    const facade = runInInjectionContext(this.injector, () =>
      definition.isEmbedded && descriptor.isEmbedded ? new DynamicEmbeddedEntityFacade(definition, descriptor) : new DynamicEntityFacade(definition, descriptor),
    );

    // Through the facade's own getter rather than by assignment: it is what binds the store into the
    // descriptor, and reading it here means every consumer of the descriptor finds a store already on it.
    const entity: ResolvedDynamicEntity = { entityName: definition.name, descriptor: facade.descriptor, facade, facadeToken: this.tokenFor(definition.name) };
    this.resolved.set(definition.name, entity);
    // Published so `BaseEntityDescriptorRegistry` can answer for this entity without depending on the
    // facades that built it — see ResolvedEntityCache for why that edge is worth avoiding.
    this.resolvedEntities.register(definition.name, { descriptor: entity.descriptor, store: facade.store });

    building.add(definition.code);
    for (const child of childDefinitionsOf(definition, byCode)) {
      if (building.has(child.code)) continue;
      this.build(child, byCode, building);
    }
    building.delete(definition.code);

    return entity;
  }
}

/** The definitions this one's `EMBEDDED_COMPONENTS` attributes carry, in attribute order. */
function childDefinitionsOf(definition: EntityDefinition, byCode: ReadonlyMap<string, EntityDefinition>): EntityDefinition[] {
  return (definition.attributes ?? [])
    .filter((attribute) => attribute.formControlType === 'EMBEDDED_COMPONENTS' && !!attribute.linkedEntityType)
    .map((attribute) => byCode.get(attribute.linkedEntityType as string))
    .filter((child): child is EntityDefinition => !!child);
}
