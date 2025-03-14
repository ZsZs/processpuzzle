import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap } from 'rxjs';
import { patchState } from '@ngrx/signals';
import { tapResponse } from '@ngrx/operators';
import { HttpErrorResponse } from '@angular/common/http';

export const updateEntity = <Entity extends BaseEntity>(store: any, repository: BaseEntityService<Entity>) => {
  return rxMethod<Entity>(
    pipe(
      switchMap((entity) => {
        patchState(store, { isLoading: true, error: undefined });
        return repository.update(entity).pipe(
          tapResponse<Entity>({
            next: (updatedEntity: Entity) => {
              const currentEntities = store.entities();
              const indexToUpdate = currentEntities.findIndex((item: Entity) => item.id === entity.id);
              currentEntities[indexToUpdate] = updatedEntity;
              patchState(store, { entities: [...currentEntities], isLoading: false });
            },
            error: (error) => patchState(store, { error: (error as HttpErrorResponse).message }),
            finalize: () => patchState(store, { isLoading: false }),
          }),
        );
      }),
    ),
  );
};
