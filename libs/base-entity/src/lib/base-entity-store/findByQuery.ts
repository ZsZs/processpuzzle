import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { BaseEntityLoadResponse, BaseEntityQueryCondition } from '../base-entity-service/base-entity-load-response';
import { pipe, switchMap, tap } from 'rxjs';
import { patchState } from '@ngrx/signals';
import { tapResponse } from '@ngrx/operators';
import { HttpErrorResponse } from '@angular/common/http';

export const findByQuery = <Entity extends BaseEntity>(store: any, repository: BaseEntityService<Entity>) => {
  return rxMethod<BaseEntityQueryCondition>(
    pipe(
      tap((query: BaseEntityQueryCondition) => patchState(store, { page: query.page, isLoading: true, error: undefined })),
      //        debounceTime(1000),
      //        distinctUntilChanged(),
      switchMap((parameters) =>
        repository.findByQuery(parameters).pipe(
          tapResponse({
            next: (response: BaseEntityLoadResponse<Entity> | Entity[] | Entity) => {
              if (Object.prototype.hasOwnProperty.call(response, 'content')) {
                const baseEntityResponse = response as BaseEntityLoadResponse<Entity>;
                patchState(store, {
                  entities: baseEntityResponse.content,
                  page: baseEntityResponse.page,
                  pageSize: baseEntityResponse.pageSize,
                  totalPageCount: baseEntityResponse.totalPageCount,
                  isLoading: false,
                });
              } else {
                const entities = (response as Entity[]).slice();
                patchState(store, {
                  entities,
                  page: undefined,
                  pageSize: entities.length,
                  totalPageCount: undefined,
                  isLoading: false,
                });
              }
            },
            error: (error: HttpErrorResponse) => patchState(store, { error: error.message }),
            finalize: () => patchState(store, { isLoading: false }),
          }),
        ),
      ),
    ),
  );
};
