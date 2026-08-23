import { inject, Injectable, Injector, ProviderToken } from '@angular/core';
import type { EmbeddedChildRoute } from '../base-entity.routes';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptor, EntityTabDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityFacade } from '../base-entity-facade/base-entity-facade';
import { BASE_ENTITY_FACADE_REGISTRY } from '../base-entity-facade/base-entity-facade-registry';
import { DynamicEntityRegistry } from '../base-entity-definition/dynamic-entity.registry';
import { ENTITY_TAB_CONTRIBUTORS, type EntityTabContributor } from './entity-tab-contributor';

/** Everything needed to mount one entity's List and Details screens somewhere. */
export interface EntityScreens {
  descriptor: BaseEntityDescriptor;
  /** The branches to hand to `baseEntityRoutes`, one per `EMBEDDED_COMPONENTS` attribute, recursively. */
  embeddedChildren: EmbeddedChildRoute[];
  /**
   * The descriptor's own extra tabs plus whatever the registered {@link EntityTabContributor}s offered —
   * the value to hand `baseEntityRoutes`, so that every tab the tab bar renders a link for has a route
   * answering to it. Mirrors `descriptor.extraTabs`, which this resolver has already updated.
   */
  extraTabs: EntityTabDescriptor[];
}

/**
 * Resolves an entity by name to its descriptor and the embedded branches below it — **whether the entity is
 * compiled in or exists only as metadata**.
 *
 * This is the seam that keeps the run-time shell from having to care about the difference. `base-app`'s
 * route renderer asks for an entity by the name an `AppDefinition` route gave it and gets the same answer
 * either way: a descriptor with a store bound, and the `EmbeddedChildRoute[]` that `baseEntityRoutes`
 * already knows how to mount. A tenant may perfectly well have an application whose screens are half
 * compiled (`Document`, with its content editor) and half authored, and neither has to know about the other.
 *
 * Compile-time facades win. If an entity name is in `BASE_ENTITY_FACADE_REGISTRY`, that registration is the
 * host application's explicit decision — it may attach extra tabs or a hand-tuned layout a synthesized
 * descriptor cannot know about — and a definition of the same name does not override it.
 */
@Injectable({ providedIn: 'root' })
export class EntityScreenResolver {
  private readonly facadeRegistry = inject(BASE_ENTITY_FACADE_REGISTRY);
  private readonly dynamicRegistry = inject(DynamicEntityRegistry);
  private readonly injector = inject(Injector);
  private readonly tabContributors = inject(ENTITY_TAB_CONTRIBUTORS, { optional: true }) ?? [];

  /** `undefined` when neither a facade nor a definition answers to `entityName`. */
  async resolve(entityName: string | undefined): Promise<EntityScreens | undefined> {
    if (!entityName) return undefined;

    const descriptor = this.compileTimeDescriptorOf(entityName) ?? (await this.dynamicRegistry.resolve(entityName))?.descriptor;
    if (!descriptor) return undefined;

    return { descriptor, embeddedChildren: this.embeddedChildrenOf(descriptor), extraTabs: await this.extraTabsOf(descriptor) };
  }

  /**
   * The descriptor's extra tabs after every contributor has been asked — and **written back onto the
   * descriptor**, because that is what `BaseEntityTabsComponent` renders the links from. The routes are
   * built from the returned array, so the two cannot disagree.
   *
   * Deduplicated by segment, and mutated in place rather than replaced: a compile-time descriptor is a
   * singleton that may be resolved more than once (two routes mounting the same entity, or base-app
   * re-rendering after a definition changed), and both a duplicated tab and a descriptor whose array
   * identity changed under a rendered tab bar are bugs this has already had.
   *
   * A contributor that throws is skipped with the others left intact: it is offering a screen, and an
   * entity's List and Details are not worth failing over one feature that could not make up its mind.
   */
  private async extraTabsOf(descriptor: BaseEntityDescriptor): Promise<EntityTabDescriptor[]> {
    const contributed = await Promise.all(this.tabContributors.map((contributor) => this.tabsFrom(contributor, descriptor)));
    const bySegment = new Map(descriptor.extraTabs.map((tab) => [tab.segment, tab]));
    contributed.flat().forEach((tab) => {
      if (!bySegment.has(tab.segment)) bySegment.set(tab.segment, tab);
    });

    descriptor.extraTabs.splice(0, descriptor.extraTabs.length, ...bySegment.values());
    return descriptor.extraTabs;
  }

  private async tabsFrom(contributor: EntityTabContributor, descriptor: BaseEntityDescriptor): Promise<EntityTabDescriptor[]> {
    try {
      return await contributor.tabsFor(descriptor);
    } catch {
      return [];
    }
  }

  /**
   * The branches for one descriptor's embedded children — **one level of them**.
   *
   * Each branch carries a *thunk* for the level below it rather than an expanded tree, which is what
   * `EmbeddedChildRoute.children` is for and what makes the depth unbounded: `baseEntityRoutes` calls the
   * thunk from a `loadChildren`, once per navigation into that level. So a child type that nests inside
   * itself — base-app's `App Nav Item`, or any definition whose `EMBEDDED_COMPONENTS` attribute names its
   * own — is finite at any moment and expands as far as the user drills. An eagerly built tree could not
   * express that structure at all, and would not terminate trying.
   *
   * A child with neither a facade nor a resolved definition is **skipped**: its rows are still listed on
   * the owner's form (they are part of the owner's payload), they just cannot be opened. That is a better
   * reading of an incomplete aggregate than refusing to mount the owner's screens at all.
   */
  private embeddedChildrenOf(descriptor: BaseEntityDescriptor): EmbeddedChildRoute[] {
    return descriptor
      .embeddedAttrDescriptors()
      .map((attrDescriptor) => attrDescriptor.linkedEntityType)
      .filter((entityName): entityName is string => !!entityName)
      .flatMap((entityName) => this.childRouteOf(entityName) ?? []);
  }

  private childRouteOf(entityName: string): EmbeddedChildRoute | undefined {
    const facade = this.facadeTokenOf(entityName);
    const descriptor = this.descriptorOf(entityName);
    if (!facade || !descriptor) return undefined;

    return { entityName, facade, children: () => this.embeddedChildrenOf(descriptor) };
  }

  /** Only the *compile-time* descriptor: `BaseEntityDescriptorRegistry` would answer for both. */
  private compileTimeDescriptorOf(entityName: string): BaseEntityDescriptor | undefined {
    return this.compileTimeFacadeOf(entityName)?.descriptor;
  }

  private compileTimeFacadeOf(entityName: string): BaseEntityFacade<BaseEntity> | undefined {
    const token = this.facadeRegistry[entityName];
    return token ? (this.injector.get(token, null, { optional: true }) ?? undefined) : undefined;
  }

  private descriptorOf(entityName: string): BaseEntityDescriptor | undefined {
    return this.compileTimeDescriptorOf(entityName) ?? this.dynamicRegistry.descriptorOf(entityName);
  }

  private facadeTokenOf(entityName: string): ProviderToken<BaseEntityFacade<BaseEntity>> | undefined {
    return this.facadeRegistry[entityName] ?? this.dynamicRegistry.facadeTokenOf(entityName);
  }
}
