import { patchState } from '@ngrx/signals';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';

export const addEntity = <Entity extends BaseEntity>(store: any, repository: BaseEntityService<Entity>) => {
  return async (entity: Entity): Promise<PersistedEntity<Entity> | undefined> => {
    patchState(store, { isLoading: true, error: undefined });
    try {
      const savedEntity = await firstValueFrom(repository.add(entity));
      patchState(store, { entities: store.entities().concat([savedEntity]), isLoading: false });
      return savedEntity;
    } catch (error) {
      patchState(store, { error: (error as HttpErrorResponse).message, isLoading: false });
      return undefined;
    }
  };
};
