import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { firstValueFrom } from 'rxjs';
import { patchState } from '@ngrx/signals';
import { httpErrorMessage } from '@processpuzzle/util';
import { EntityKeyResolver, entityKeyById, EntityStoreHandle } from './base-entity.store';

/**
 * Replaces one row with what the repository answers for it now. For a change the server made on its own — a
 * fired state transition rewrites the state attribute *and* bumps the version — after which the row this store
 * holds would show a stale state and fail its next save with a 409.
 */
export const reloadEntity = <Entity extends BaseEntity>(store: EntityStoreHandle<Entity>, repository: BaseEntityService<Entity>, keyOf: EntityKeyResolver<Entity> = entityKeyById) => {
  return async (id: string): Promise<PersistedEntity<Entity> | undefined> => {
    try {
      const reloaded = await firstValueFrom(repository.findById(id));
      if (!reloaded) return undefined;
      const entities = [...store.entities()];
      const index = entities.findIndex((item) => keyOf(item) === id);
      if (index >= 0) entities[index] = reloaded;
      else entities.push(reloaded);
      patchState(store, { entities });
      return reloaded;
    } catch (error) {
      patchState(store, { error: httpErrorMessage(error) });
      return undefined;
    }
  };
};
