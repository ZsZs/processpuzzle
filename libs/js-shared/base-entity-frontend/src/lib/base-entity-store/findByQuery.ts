import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { BaseEntityLoadResponse, BaseEntityQueryCondition } from '../base-entity-service/base-entity-load-response';
import { concat, concatMap, map, Observable, of, pipe, range, switchMap, tap, toArray } from 'rxjs';
import { patchState } from '@ngrx/signals';
import { tapResponse } from '@ngrx/operators';
import { httpErrorMessage } from '@processpuzzle/util';
import { EntityStoreHandle } from './base-entity.store';

/** What a read of every page asks for per request, and the most pages it follows: 20,000 rows is not a screen's worth. */
const ALL_PAGES_PAGE_SIZE = 200;
const ALL_PAGES_LIMIT = 100;

type LoadAnswer<Entity extends BaseEntity> = BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[] | PersistedEntity<Entity>;

const isPaged = <Entity extends BaseEntity>(answer: LoadAnswer<Entity>): answer is BaseEntityLoadResponse<PersistedEntity<Entity>> => Object.hasOwn(answer as object, 'content');

/**
 * Every page of a query as one answer. The first page says how many there are; the others follow in order, so a
 * newer query cancels the whole read, as it does a single request.
 */
function readAllPages<Entity extends BaseEntity>(repository: BaseEntityService<Entity>, query: BaseEntityQueryCondition): Observable<LoadAnswer<Entity>> {
  const pageSize = query.pageSize ?? ALL_PAGES_PAGE_SIZE;
  const read = (page: number) => repository.findByQuery({ ...query, allPages: false, page, pageSize });
  return read(0).pipe(
    switchMap((first) => {
      if (!isPaged(first)) return of(first);
      const remaining = Math.max(0, Math.min(first.totalPages ?? 1, ALL_PAGES_LIMIT) - 1);
      return concat(of(first), range(1, remaining).pipe(concatMap((page) => read(page)))).pipe(
        toArray(),
        map((answers): LoadAnswer<Entity> => {
          const content = answers.flatMap((answer) => (isPaged(answer) ? answer.content : []));
          return { content, number: 0, size: content.length, totalElements: first.totalElements, totalPages: 1 };
        }),
      );
    }),
  );
}

/**
 * `onLoaded` runs after each answer, successful or not, once the rows (if any) are in the store: for whoever asked for a
 * row while the list was on its way.
 */
export const findByQuery = <Entity extends BaseEntity>(store: EntityStoreHandle<Entity>, repository: BaseEntityService<Entity>, onLoaded?: () => void) => {
  return rxMethod<BaseEntityQueryCondition>(
    pipe(
      tap((query: BaseEntityQueryCondition) => patchState(store, { number: query.page, isLoading: true, error: undefined })),
      //        debounceTime(1000),
      //        distinctUntilChanged(),
      switchMap((parameters) =>
        (parameters.allPages ? readAllPages(repository, parameters) : repository.findByQuery(parameters)).pipe(
          tapResponse({
            next: (response: BaseEntityLoadResponse<PersistedEntity<Entity>> | PersistedEntity<Entity>[] | PersistedEntity<Entity>) => {
              if (Object.hasOwn(response as object, 'content')) {
                const baseEntityResponse = response as BaseEntityLoadResponse<PersistedEntity<Entity>>;
                patchState(store, {
                  entities: baseEntityResponse.content,
                  number: baseEntityResponse.number,
                  size: baseEntityResponse.size,
                  totalElements: baseEntityResponse.totalElements,
                  totalPages: baseEntityResponse.totalPages,
                  isLoading: false,
                });
              } else {
                const entities = (response as Entity[]).slice();
                patchState(store, {
                  entities,
                  number: undefined,
                  size: entities.length,
                  totalElements: entities.length,
                  totalPages: undefined,
                  isLoading: false,
                });
              }
              onLoaded?.();
            },
            error: (error: unknown) => {
              patchState(store, { error: httpErrorMessage(error) });
              onLoaded?.();
            },
            finalize: () => patchState(store, { isLoading: false }),
          }),
        ),
      ),
    ),
  );
};
