import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { firstValueFrom } from 'rxjs';
import { patchState } from '@ngrx/signals';
import { httpErrorMessage } from '@processpuzzle/util';
import { EntityKeyResolver, entityKeyById, EntityStoreHandle } from './base-entity.store';

export const updateEntity = <Entity extends BaseEntity>(store: EntityStoreHandle<Entity>, repository: BaseEntityService<Entity>, keyOf: EntityKeyResolver<Entity> = entityKeyById) => {
  return async (entity: PersistedEntity<Entity>): Promise<PersistedEntity<Entity> | undefined> => {
    patchState(store, { isLoading: true, error: undefined });
    try {
      const updatedEntity = await firstValueFrom(repository.update(entity));
      const currentEntities = [...store.entities()];
      // An unkeyed entity replaces nothing: matching two `undefined`s would overwrite the first row.
      const key = keyOf(entity);
      const indexToUpdate = key === undefined ? -1 : currentEntities.findIndex((item) => keyOf(item) === key);
      if (indexToUpdate >= 0) currentEntities[indexToUpdate] = updatedEntity;
      patchState(store, { entities: currentEntities, isLoading: false });
      return updatedEntity;
    } catch (error) {
      patchState(store, { error: httpErrorMessage(error), isLoading: false });
      return undefined;
    }
  };
};
