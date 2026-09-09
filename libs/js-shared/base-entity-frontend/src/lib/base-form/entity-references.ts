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

/**
 * The id one raw item of a to-many reference attribute stands for — the same answer
 * {@link normalizeEntityReferences} puts in `id`, without building the normalized object.
 *
 * Needed because *removing* a row has to filter the attribute's **raw** value, not the normalized copy:
 * the payload has to keep the shape the contract asked for, so an attribute holding bare ids must still
 * hold bare ids afterwards. Comparing `item.id` directly is what broke — on a `string[]` attribute every
 * item's `.id` is `undefined`, so the filter matched nothing and the row could never be detached.
 */
export function referenceIdOf(item: unknown, referenceIdField?: string): string | undefined {
  if (typeof item === 'string' || typeof item === 'number') return String(item);
  if (!item || typeof item !== 'object') return undefined;

  const record = item as Record<string, unknown>;
  const idField = referenceIdField ?? 'id';
  const value = 'id' in record ? record['id'] : record[idField];
  return value === undefined || value === null ? undefined : String(value);
}
