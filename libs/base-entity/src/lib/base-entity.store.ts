import { BaseEntity } from './base-entity/base-entity';
import { patchState, signalStoreFeature, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { computed, inject, InjectionToken, ProviderToken } from '@angular/core';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { tapResponse } from '@ngrx/operators';
import { BaseEntityService } from './base-entity.service';
import { BaseEntityLoadResponse } from './base-entity-load-response';
import { MatTableDataSource } from '@angular/material/table';

export const BASE_ENTITY_STORE = new InjectionToken<any>('BASE_ENTITY_STORE');

export interface EntityStoreState<Entity extends BaseEntity> {
  entities: Entity[];
  page: number;
  pageSize: number;
  totalPageCount: number;
  currentId: string | undefined;
  currentEntity: Entity | undefined;
  isLoading: boolean;
  error: string | undefined;
  selectedEntities: Array<Entity>;
}

export interface LoadMethodParameters {
  path?: Map<string, string>;
  filter?: Map<string, string>;
  page?: number;
}

export function BaseEntityStore<Entity extends BaseEntity>(entityType: new () => Entity, repositoryType: ProviderToken<BaseEntityService<Entity>>) {
  return signalStoreFeature(
    withState<EntityStoreState<Entity>>({
      entities: [],
      page: 0,
      pageSize: 5,
      totalPageCount: 0,
      currentEntity: undefined,
      currentId: undefined,
      isLoading: false,
      error: undefined,
      selectedEntities: [],
    }),
    //NOSONAR there is no simple way to reduce nesting here
    withMethods((store, repository = inject(repositoryType)) => ({
      clearCurrentEntity: () => patchState(store, { currentEntity: undefined }),
      createEntity: (): Entity => new entityType(),
      delete: rxMethod<string>(
        pipe(
          tap(() => patchState(store, { isLoading: true, error: undefined })),
          switchMap((id: string) => {
            return repository.delete(id).pipe(
              tapResponse({
                next: () => {
                  const remainingEntities = store.entities().filter((entity) => entity.id !== id);
                  patchState(store, { entities: remainingEntities, isLoading: false });
                },
                error: (error: HttpErrorResponse) => patchState(store, { error: error.message }),
                finalize: () => patchState(store, { isLoading: false }),
              }),
            );
          }),
        ),
      ),
      deleteAll: rxMethod<void>(
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
      ),
      deselectAll: () => {
        patchState(store, { selectedEntities: [] });
      },
      deselectEntity: (id: string) => {
        const subjectEntity = store.selectedEntities().find((entity) => entity.id === id);
        if (subjectEntity) {
          const index = store.selectedEntities().indexOf(subjectEntity, 0);
          if (index > -1) {
            const selectedEntities = store.selectedEntities().concat([]);
            selectedEntities.splice(index, 1);
            patchState(store, { selectedEntities });
          }
        }
      },
      load: rxMethod<LoadMethodParameters>(
        pipe(
          tap((parameters) => patchState(store, { page: parameters.page, isLoading: true, error: undefined })),
          //        debounceTime(1000),
          //        distinctUntilChanged(),
          switchMap((parameters) =>
            repository.findAll(parameters.path, parameters.filter, parameters.page).pipe(
              tapResponse({
                next: (response: BaseEntityLoadResponse<Entity> | Entity[]) => {
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
      ),
      loadById: (id: string): Entity | undefined => {
        return store.entities().find((entity) => entity.id === id);
      },
      resetErrorState: () => patchState(store, { error: undefined }),
      save: rxMethod<Entity>(
        pipe(
          switchMap((entity) => {
            patchState(store, { isLoading: true, error: undefined });
            return repository.save(entity).pipe(
              tapResponse({
                next: (savedEntity: Entity) => {
                  patchState(store, { entities: store.entities().concat([savedEntity]), isLoading: false });
                },
                error: (error: HttpErrorResponse) => patchState(store, { error: error.message }),
                finalize: () => patchState(store, { isLoading: false }),
              }),
            );
          }),
        ),
      ),
      selectEntity: (id: string) => {
        const foundEntity = store.entities() && store.entities().length > 0 ? store.entities().filter((entity) => entity.id === id) : undefined;
        if (foundEntity && foundEntity.length == 1 && store.selectedEntities().indexOf(foundEntity[0]) == -1) {
          patchState(store, { selectedEntities: store.selectedEntities().concat(foundEntity) });
        }
      },
      setCurrentEntity: (id: string): void => {
        const foundEntity = store.entities() && store.entities().length > 0 ? store.entities().filter((entity) => entity.id === id) : undefined;
        if (foundEntity && foundEntity.length > 0) {
          patchState(store, { currentEntity: foundEntity[0], currentId: foundEntity[0].id });
        } else patchState(store, { currentEntity: undefined, currentId: undefined });
      },
      update: rxMethod<Entity>(
        pipe(
          switchMap((entity) => {
            patchState(store, { isLoading: true, error: undefined });
            return repository.update(entity).pipe(
              tapResponse({
                next: (updatedEntity: Entity) => {
                  const currentEntities = store.entities();
                  const indexToUpdate = currentEntities.findIndex((item) => item.id === entity.id);
                  currentEntities[indexToUpdate] = updatedEntity;
                  patchState(store, { entities: [...currentEntities], isLoading: false });
                },
                error: (error: HttpErrorResponse) => patchState(store, { error: error.message }),
                finalize: () => patchState(store, { isLoading: false }),
              }),
            );
          }),
        ),
      ),
    })),
    withHooks((store) => ({
      onInit: () => {
        const path = new Map<string, string>([]);
        const filter = new Map<string, string>([]);
        store.load({ path, filter });
      },
    })),
    withComputed((store) => ({
      countOfEntities: computed(() => store.entities().length),
      matTableDataSource: computed(() => new MatTableDataSource<Entity>(store.entities())),
    })),
  );
}
