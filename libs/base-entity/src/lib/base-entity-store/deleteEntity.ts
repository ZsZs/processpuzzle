import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { patchState } from '@ngrx/signals';
import { tapResponse } from '@ngrx/operators';
import { HttpErrorResponse } from '@angular/common/http';

export const deleteEntity = <Entity extends BaseEntity>(store: any, repository: BaseEntityService<Entity>) => {
  return rxMethod<string>(
    pipe(
      tap(() => patchState(store, { isLoading: true, error: undefined })),
      switchMap((id: string) => {
        return repository.delete(id).pipe(
          tapResponse({
            next: () => {
              const remainingEntities = store.entities().filter((entity: Entity) => entity.id !== id);
              patchState(store, { entities: remainingEntities, isLoading: false });
            },
            error: (error: HttpErrorResponse) => patchState(store, { error: error.message }),
            finalize: () => patchState(store, { isLoading: false }),
          }),
        );
      }),
    ),
  );
};
