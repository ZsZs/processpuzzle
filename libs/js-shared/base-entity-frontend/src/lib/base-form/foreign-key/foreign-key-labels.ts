import { inject, Injectable } from '@angular/core';
import { BaseEntity, PersistedEntity } from '../../base-entity/base-entity';
import { BaseEntityDescriptorRegistry } from '../../base-entity-facade/base-entity-descriptor.registry';

interface RelatedEntityStore {
  entities?: () => PersistedEntity<BaseEntity>[];
  loadById?: (id: string) => PersistedEntity<BaseEntity> | undefined;
}

/**
 * What a FOREIGN_KEY value is shown as: the linked object's identifying attribute — a race's name, a boat's
 * sail number — rather than its id. Shared by the form control and the list, so that the two show the same.
 *
 * Reads the linked type's store through {@link BaseEntityDescriptorRegistry}, so it is reactive when called
 * from a template or a `computed`: the label replaces the id as soon as the linked store has loaded. Falls
 * back to the id when the linked type is not registered or resolved, or the object is not (yet) loaded.
 */
@Injectable({ providedIn: 'root' })
export class ForeignKeyLabels {
  private readonly registry = inject(BaseEntityDescriptorRegistry);

  /**
   * @param linkedEntityType the attribute's linked entity name
   * @param value            the attribute's value, the linked object's id
   * @param known            the linked object when the caller already holds it, e.g. one just selected
   * @param snapshot         the copy of the linked object the referencing record kept (`<attribute>$snapshot`); shown
   *                         when the linked object is not visible here, instead of the bare id
   */
  labelOf(
    linkedEntityType: string | undefined,
    value: unknown,
    known?: PersistedEntity<BaseEntity>,
    snapshot?: Record<string, unknown> | null,
  ): string {
    const id = value === undefined || value === null ? '' : String(value);
    if (!id) return '';
    const related = known ?? this.find(linkedEntityType, id);
    if (!related) return snapshot ? this.snapshotLabel(linkedEntityType, snapshot, id) : id;

    const attrName = this.registry.getDescriptor(linkedEntityType)?.componentIdentification() ?? '';
    const label = attrName ? (related as unknown as Record<string, unknown>)[attrName] : undefined;
    if (typeof label === 'string' && label !== '') return label;
    if (typeof label === 'number' || typeof label === 'boolean' || typeof label === 'bigint') return String(label);
    return related.id;
  }

  /** The identifying attribute of the snapshot, else its first text value, else the id. */
  private snapshotLabel(linkedEntityType: string | undefined, snapshot: Record<string, unknown>, id: string): string {
    const attrName = this.registry.getDescriptor(linkedEntityType)?.componentIdentification() ?? '';
    const preferred = attrName ? snapshot[attrName] : undefined;
    const candidate = preferred ?? Object.values(snapshot).find((value) => typeof value === 'string' && value !== '');
    if (typeof candidate === 'string' && candidate !== '') return candidate;
    if (typeof candidate === 'number' || typeof candidate === 'boolean' || typeof candidate === 'bigint') return String(candidate);
    return id;
  }

  private find(linkedEntityType: string | undefined, id: string): PersistedEntity<BaseEntity> | undefined {
    const store = this.registry.getStore<RelatedEntityStore>(linkedEntityType);
    if (!store) return undefined;
    if (typeof store.loadById === 'function') return store.loadById(id);
    if (typeof store.entities === 'function') return store.entities().find((entity) => entity.id === id);
    return undefined;
  }
}
