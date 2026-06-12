import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { pipe, switchMap } from 'rxjs';
import { patchState } from '@ngrx/signals';
import { tapResponse } from '@ngrx/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { rxMethod } from '@ngrx/signals/rxjs-interop';

export const deleteAllEntities = <Entity extends BaseEntity>(store: any, repository: BaseEntityService<Entity>) => {
  return rxMethod<void>(
    pipe(
      switchMap(() => {
        patchState(store, { isLoading: true, error: undefined });
        return repository.deleteAll().pipe(
          tapResponse({
            next: () => patchState(store, { entities: [], isLoading: false }),
            error: (error: HttpErrorResponse) => patchState(store, { error: error.message }),
            finalize: () => patchState(store, { isLoading: false }),
          }),
        );
      }),
    ),
  );
};
