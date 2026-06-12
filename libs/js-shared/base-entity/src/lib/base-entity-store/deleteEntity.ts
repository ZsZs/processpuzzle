import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { firstValueFrom } from 'rxjs';
import { patchState } from '@ngrx/signals';
import { HttpErrorResponse } from '@angular/common/http';

export const deleteEntity = <Entity extends BaseEntity>(store: any, repository: BaseEntityService<Entity>) => {
  return async (id: string): Promise<void> => {
    patchState(store, { isLoading: true, error: undefined });
    try {
      await firstValueFrom(repository.delete(id));
      const remainingEntities = store.entities().filter((entity: Entity) => entity.id !== id);
      const remainingSelectedEntities = store.selectedEntities().filter((entity: Entity) => entity.id !== id);
      patchState(store, {
        currentEntity: undefined,
        currentId: undefined,
        entities: remainingEntities,
        selectedEntities: remainingSelectedEntities,
        isLoading: false,
      });
    } catch (error) {
      patchState(store, { error: (error as HttpErrorResponse).message, isLoading: false });
    }
  };
};
