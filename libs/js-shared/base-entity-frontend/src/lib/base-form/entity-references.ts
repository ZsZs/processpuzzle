import { assertPersistedEntity, BaseEntity, PersistedEntity } from '../base-entity/base-entity';

/**
 * Normalizes the heterogeneous shapes a to-many reference attribute may hold into `{ id }` objects.
 *
 * A referencing attribute is written by several parties — the seed data, the navigator round-trip, a form
 * snapshot — so its items arrive as bare ids, as whole entities, or as objects identified by something other
 * than `id` (see `referenceIdField`, needed by entities such as `App Region` that have no `id` at all).
 * Shared by the `RELATED_ENTITIES` and `COMPONENTS` controls, which differ in what a row *means*, not in how
 * the reference is written down.
 */
export function normalizeEntityReferences(value: unknown, referenceIdField?: string): PersistedEntity<BaseEntity>[] {
  if (!Array.isArray(value)) return [];

  const idField = referenceIdField ?? 'id';
  const normalized = value.map((item) => {
    if (typeof item === 'string' || typeof item === 'number') {
      return { id: String(item) };
    }
    if (idField !== 'id' && item && typeof item === 'object' && !('id' in item)) {
      return { ...item, id: String((item as Record<string, unknown>)[idField] ?? '') };
    }
    return item;
  });
  normalized.forEach((reference) => assertPersistedEntity(reference));
  return normalized;
}
