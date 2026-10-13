import { inject, Injectable, InjectionToken } from '@angular/core';
import { DynamicEntityRegistry } from './dynamic-entity.registry';

/**
 * Whether the screens of the organization's own entity types — the ones built at run time from definitions — are
 * read-only in this page. A host sets it while it shows *draft* definitions: there the screens are a preview of
 * definitions not yet released, over the organization's real objects, and a save through a draft's attributes and
 * rules would write live data the running application does not yet expect.
 *
 * `false` unless a host provides it. The designer's own screens are never affected: they are compiled into the
 * application, not built from definitions.
 */
export const ENTITY_PREVIEW_READ_ONLY = new InjectionToken<boolean>('ENTITY_PREVIEW_READ_ONLY', {
  providedIn: 'root',
  factory: () => false,
});

/** Answers, per entity type, whether its screens may only be looked at; see {@link ENTITY_PREVIEW_READ_ONLY}. */
@Injectable({ providedIn: 'root' })
export class EntityPreviewMode {
  private readonly previewReadOnly = inject(ENTITY_PREVIEW_READ_ONLY);
  private readonly dynamicEntities = inject(DynamicEntityRegistry);

  /** True for an entity type built from a definition, while the host previews drafts. */
  isReadOnly(entityName: string | undefined): boolean {
    return this.previewReadOnly && this.dynamicEntities.descriptorOf(entityName) !== undefined;
  }
}
