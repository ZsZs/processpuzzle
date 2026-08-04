import { inject, Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { EmbeddedRouteContext, EmbeddedRouteLevel, readEmbeddedRouteChain, resolveEmbeddedRouteContext } from './embedded-route-context';

/** An embedded level resolved against the live aggregate: where its rows are, and who owns the document. */
export interface ResolvedEmbeddedAggregate {
  context: EmbeddedRouteContext;
  rootStore: BaseEntityStoreApi<BaseEntity>;
  rootPayload: unknown;
}

/**
 * Resolves an embedded entity's place in the aggregate that is currently open, and is the single point that
 * knows an embedded write is really a write of the **root** document.
 *
 * The lookup is done per call rather than held as state: the stores are root singletons, while the row an
 * embedded store stands for changes with every navigation.
 */
@Injectable({ providedIn: 'root' })
export class EmbeddedAggregateAccessor {
  private readonly router = inject(Router);
  private readonly descriptorRegistry = inject(BaseEntityDescriptorRegistry);

  /** `undefined` when no aggregate holding `entityName` is open — the caller decides whether that is an error. */
  resolve(entityName: string): ResolvedEmbeddedAggregate | undefined {
    const levels = this.levelsFor(entityName);
    if (levels.length < 2) return undefined;

    const rootEntityName = levels[0].entityName;
    const rootId = levels[0].entityId;
    const rootStore = this.descriptorRegistry.getStore<BaseEntityStoreApi<BaseEntity>>(rootEntityName);
    if (!rootStore || !rootId) return undefined;

    // `loadById` before `currentEntity` so a write does not depend on the status bar's selection having
    // caught up with the route.
    const rootPayload = rootStore.loadById(rootId) ?? rootStore.currentEntity();
    if (!rootPayload) return undefined;

    const context = resolveEmbeddedRouteContext(levels, rootPayload, (name) => this.descriptorRegistry.getDescriptor(name));
    if (!context) return undefined;

    return { context, rootStore, rootPayload };
  }

  /**
   * The store of the document `entityName`'s rows live in, resolved from the route alone.
   *
   * Unlike {@link resolve} this answers before the document has loaded, which is what lets a caller depend on
   * it reactively: an embedded store's rows are a projection of the containing document, so whoever reads
   * them has to be told when that document arrives or changes.
   */
  rootStoreFor(entityName: string): BaseEntityStoreApi<BaseEntity> | undefined {
    const levels = this.levelsFor(entityName);
    if (levels.length < 2) return undefined;

    return this.descriptorRegistry.getStore<BaseEntityStoreApi<BaseEntity>>(levels[0].entityName);
  }

  /** Persists the aggregate with `rootPayload` replaced — an embedded row has no endpoint of its own. */
  async writeRoot(resolved: ResolvedEmbeddedAggregate, rootPayload: Record<string, unknown>): Promise<void> {
    // The payload is the root entity with one branch rebuilt, so it carries the same id it was read by.
    await resolved.rootStore.update(rootPayload as unknown as PersistedEntity<BaseEntity>);
  }

  /**
   * The URL chain, ending at `entityName`.
   *
   * The route chain does not always reach the entity being asked about: the rows of a *nested* embedded
   * attribute are listed on their owner's form, so `Embedded Detail`'s store is read while the deepest
   * active route is still `Embedded Component`. Appending the level in that case is what lets one form host
   * the list of the next level down.
   */
  private levelsFor(entityName: string): EmbeddedRouteLevel[] {
    const levels = readEmbeddedRouteChain(this.deepestActivatedRoute());

    // The deepest occurrence: a child type may nest inside itself, and the innermost one is the owner here.
    let levelIndex = -1;
    levels.forEach((level, index) => {
      if (level.entityName === entityName) levelIndex = index;
    });
    if (levelIndex >= 0) return levels.slice(0, levelIndex + 1);

    return [...levels, { entityName, entityId: undefined }];
  }

  private deepestActivatedRoute(): ActivatedRouteSnapshot {
    let snapshot = this.router.routerState.snapshot.root;
    while (snapshot.firstChild) snapshot = snapshot.firstChild;
    return snapshot;
  }
}
