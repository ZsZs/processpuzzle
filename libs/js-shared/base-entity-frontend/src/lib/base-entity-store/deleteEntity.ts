import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { firstValueFrom } from 'rxjs';
import { patchState } from '@ngrx/signals';
import { EntityKeyResolver, entityKeyById, EntityStoreHandle } from './base-entity.store';

export const deleteEntity = <Entity extends BaseEntity>(store: EntityStoreHandle<Entity>, repository: BaseEntityService<Entity>, keyOf: EntityKeyResolver<Entity> = entityKeyById) => {
  return async (id: string): Promise<void> => {
    patchState(store, { isLoading: true, error: undefined });
    try {
      await firstValueFrom(repository.delete(id));
      const remainingEntities = store.entities().filter((entity) => keyOf(entity) !== id);
      const remainingSelectedEntities = store.selectedEntities().filter((entity) => keyOf(entity) !== id);
      patchState(store, {
        currentEntity: undefined,
        currentId: undefined,
        entities: remainingEntities,
        selectedEntities: remainingSelectedEntities,
        isLoading: false,
      });
    } catch (error) {
      let errorMessage = 'Unknown error';
      if (error instanceof Error) {
        errorMessage = error.message;
      } else if (error && typeof error === 'object' && 'message' in error && typeof (error as Record<string, unknown>)['message'] === 'string') {
        errorMessage = (error as Record<string, unknown>)['message'] as string;
      }
      patchState(store, { error: errorMessage, isLoading: false });
    }
  };
};
