import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { patchState } from '@ngrx/signals';
import { EntityKeyResolver, entityKeyById, EntityStoreHandle } from './base-entity.store';

/**
 * Replaces a row in the store without going to the repository, and marks it unsaved. See
 * `BaseEntityStoreApi.stage`. An unkeyed entity has no row to replace, so it is left alone rather than
 * appended: staging is for a document the store already holds.
 */
export const stageEntity = <Entity extends BaseEntity>(store: EntityStoreHandle<Entity>, keyOf: EntityKeyResolver<Entity> = entityKeyById) => {
  return (entity: PersistedEntity<Entity>): void => {
    const key = keyOf(entity);
    if (key === undefined) return;
    const entities = [...store.entities()];
    const index = entities.findIndex((item) => keyOf(item) === key);
    if (index < 0) return;
    entities[index] = entity;
    const stagedIds = store.stagedIds().includes(key) ? store.stagedIds() : [...store.stagedIds(), key];
    patchState(store, { entities, stagedIds });
  };
};

/** The staged keys without `key` — for a row that has just been persisted, re-read or deleted. */
export const unstage = <Entity extends BaseEntity>(store: EntityStoreHandle<Entity>, key: string | undefined): string[] => {
  return key === undefined ? store.stagedIds() : store.stagedIds().filter((staged) => staged !== key);
};
