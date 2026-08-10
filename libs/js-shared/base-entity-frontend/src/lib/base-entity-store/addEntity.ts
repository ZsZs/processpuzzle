import { patchState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { EntityStoreHandle } from './base-entity.store';

export const addEntity = <Entity extends BaseEntity>(store: EntityStoreHandle<Entity>, repository: BaseEntityService<Entity>) => {
  return async (entity: Entity): Promise<PersistedEntity<Entity> | undefined> => {
    patchState(store, { isLoading: true, error: undefined });
    try {
      const savedEntity = await firstValueFrom(repository.add(entity));
      patchState(store, { entities: store.entities().concat([savedEntity]), isLoading: false });
      return savedEntity;
    } catch (error) {
      let errorMessage = 'Unknown error';
      if (error instanceof Error) {
        errorMessage = error.message;
      } else if (error && typeof error === 'object' && 'message' in error && typeof (error as Record<string, unknown>)['message'] === 'string') {
        errorMessage = (error as Record<string, unknown>)['message'] as string;
      }
      patchState(store, { error: errorMessage, isLoading: false });
      return undefined;
    }
  };
};
