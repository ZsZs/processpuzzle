import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { firstValueFrom } from 'rxjs';
import { patchState } from '@ngrx/signals';
import { HttpErrorResponse } from '@angular/common/http';
import { EntityStoreHandle } from './base-entity.store';

export const updateEntity = <Entity extends BaseEntity>(store: EntityStoreHandle<Entity>, repository: BaseEntityService<Entity>) => {
  return async (entity: PersistedEntity<Entity>): Promise<PersistedEntity<Entity> | undefined> => {
    patchState(store, { isLoading: true, error: undefined });
    try {
      const updatedEntity = await firstValueFrom(repository.update(entity));
      const currentEntities = [...store.entities()];
      const indexToUpdate = currentEntities.findIndex((item) => item.id === entity.id);
      if (indexToUpdate >= 0) currentEntities[indexToUpdate] = updatedEntity;
      patchState(store, { entities: currentEntities, isLoading: false });
      return updatedEntity;
    } catch (error) {
      patchState(store, { error: (error as HttpErrorResponse).message, isLoading: false });
      return undefined;
    }
  };
};
