import { InjectionToken } from '@angular/core';
import type { BaseEntityDescriptor, EntityTabDescriptor } from '../base-entity/base-entity.descriptor';

/**
 * Contributes extra tabs to *another* feature's entity — the cross-cutting counterpart of declaring
 * `extraTabs` on a descriptor.
 *
 * The descriptor hook covers the case where the feature owning the entity also owns the extra screen:
 * `Document` adds its content editor, a `State Machine Definition` adds the modeler. It cannot cover the
 * opposite case, which is what this is for — base-state has a screen to offer `Order`, and neither
 * base-entity nor whoever authored the `order` definition can be expected to know that. A contributor is
 * asked about every entity whose screens are mounted through {@link EntityScreenResolver}, and answers for
 * the ones it recognizes.
 *
 * **Asked once per entity, when its screens are resolved.** {@link tabsFor} may therefore be asynchronous —
 * a contributor typically has to fetch something to know whether it has anything to offer — and the answer
 * is fixed for the life of the resolved entity. A tab whose *content* varies is the tab component's
 * business; a tab whose *existence* varies while the application runs is not expressible here, and should
 * be a tab that always exists and says it has nothing to show.
 *
 * Contributed tabs are appended after the descriptor's own, so a feature's own screens keep the order it
 * declared them in, and are deduplicated by `segment`: the same contributor answering twice for one
 * descriptor — which happens whenever a compile-time descriptor, being a singleton, is resolved again —
 * must not stack the tab up.
 */
export interface EntityTabContributor {
  /** The tabs this contributor offers `descriptor`, or an empty array — the usual answer. */
  tabsFor(descriptor: BaseEntityDescriptor): Promise<EntityTabDescriptor[]> | EntityTabDescriptor[];
}

/**
 * The registered {@link EntityTabContributor}s. A `multi` token, so every feature adds its own without
 * any of them knowing about the others; absent entirely when no feature contributes, which is why every
 * reader injects it optionally.
 */
export const ENTITY_TAB_CONTRIBUTORS = new InjectionToken<readonly EntityTabContributor[]>('ENTITY_TAB_CONTRIBUTORS');
