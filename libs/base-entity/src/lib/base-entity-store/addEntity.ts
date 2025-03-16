import { patchState } from '@ngrx/signals';
import { tapResponse } from '@ngrx/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap } from 'rxjs';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { BaseEntity } from '../base-entity/base-entity';

export const addEntity = <Entity extends BaseEntity>(store: any, repository: BaseEntityService<Entity>) => {
  return rxMethod<Entity>(
    pipe(
      switchMap((entity) => {
        patchState(store, { isLoading: true, error: undefined });
        return repository.add(entity).pipe(
          tapResponse<Entity>({
            next: (savedEntity: Entity) => {
              patchState(store, { entities: store.entities().concat([savedEntity]), isLoading: false });
            },
            error: (error) => patchState(store, { error: (error as HttpErrorResponse).message }),
            finalize: () => patchState(store, { isLoading: false }),
          }),
        );
      }),
    ),
  );
};
