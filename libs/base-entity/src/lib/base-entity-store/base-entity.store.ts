import { BaseEntity } from '../base-entity/base-entity';
import { patchState, signalStoreFeature, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { computed, inject, InjectionToken, ProviderToken } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { addEntity } from './addEntity';
import { deleteEntity } from './deleteEntity';
import { deleteAllEntities } from './deleteAllEntities';
import { updateEntity } from './updateEntity';
import { findByQuery } from './findByQuery';

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
    withMethods((store, repository = inject(repositoryType)) => ({
      clearCurrentEntity: () => patchState(store, { currentEntity: undefined }),
      createEntity: (): Entity => new entityType(),
      add: addEntity(store, repository),
      delete: deleteEntity(store, repository),
      deleteAll: deleteAllEntities(store, repository),
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
      load: findByQuery(store, repository),
      loadById: (id: string): Entity | undefined => {
        return store.entities().find((entity) => entity.id === id);
      },
      resetErrorState: () => patchState(store, { error: undefined }),
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
      update: updateEntity(store, repository),
    })),
    withHooks((store) => ({
      onInit: () => {
        store.load({});
      },
    })),
    withComputed((store) => ({
      countOfEntities: computed(() => store.entities().length),
      matTableDataSource: computed(() => new MatTableDataSource<Entity>(store.entities())),
    })),
  );
}
