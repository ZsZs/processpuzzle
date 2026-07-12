import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { patchState, signalStoreFeature, withComputed, withHooks, withMethods, withState, WritableStateSource } from '@ngrx/signals';
import { computed, InjectionToken, Signal } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { addEntity } from './addEntity';
import { deleteEntity } from './deleteEntity';
import { deleteAllEntities } from './deleteAllEntities';
import { updateEntity } from './updateEntity';
import { findByQuery } from './findByQuery';
import { BaseEntityQueryCondition } from '../base-entity-service/base-entity-load-response';

export const BASE_ENTITY_STORE = new InjectionToken<unknown>('BASE_ENTITY_STORE');

export interface EntityStoreState<Entity extends BaseEntity> {
  entities: Entity[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  currentId: string | undefined;
  currentEntity: Entity | undefined;
  isLoading: boolean;
  error: string | undefined;
  selectedEntities: Array<Entity>;
}

export type EntityStoreHandle<Entity extends BaseEntity> = WritableStateSource<EntityStoreState<Entity>> & {
  entities: Signal<Entity[]>;
  selectedEntities: Signal<Entity[]>;
};

export interface BaseEntityStoreApi<Entity extends BaseEntity> {
  entities: Signal<Entity[]>;
  selectedEntities: Signal<Entity[]>;
  currentEntity: Signal<Entity | undefined>;
  currentId: Signal<string | undefined>;
  isLoading: Signal<boolean>;
  error: Signal<string | undefined>;
  number: Signal<number>;
  size: Signal<number>;
  totalElements: Signal<number>;
  totalPages: Signal<number>;
  filterKey: Signal<string | undefined>;
  activeTabs: Signal<string[]>;
  currentTab: Signal<string | undefined>;
  countOfEntities: Signal<number>;
  matTableDataSource: Signal<MatTableDataSource<Entity>>;

  add(entity: Entity): Promise<PersistedEntity<Entity> | undefined>;
  delete(id: string): Promise<void>;
  deleteAll(input?: void): void;
  deselectAll(): void;
  deselectEntity(id: string): void;
  load(query: BaseEntityQueryCondition): void;
  loadById(id: string): Entity | undefined;
  resetErrorState(): void;
  selectEntity(id: string): void;
  setCurrentEntity(id: string | undefined): void;
  update(entity: PersistedEntity<Entity>): Promise<PersistedEntity<Entity> | undefined>;
  clearCurrentEntity(): void;
  createEntity(): Entity;
  doFilter(filterKey: string): void;
  reset(): void;
  tabIsActive(tabName: string): void;
  tabIsInactive(tabName: string): void;
}

export function BaseEntityStore<Entity extends BaseEntity>(entityType: new () => Entity, getRepository: () => BaseEntityService<Entity>) {
  return signalStoreFeature(
    withState<EntityStoreState<Entity>>({
      entities: [],
      number: 0,
      size: 5,
      totalElements: 0,
      totalPages: 0,
      currentEntity: undefined,
      currentId: undefined,
      isLoading: false,
      error: undefined,
      selectedEntities: [],
    }),
    withMethods((store, repository = getRepository()) => ({
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
        const foundEntity = store.entities().length > 0 ? store.entities().filter((entity) => entity.id === id) : undefined;
        if (foundEntity?.length === 1 && !store.selectedEntities().includes(foundEntity[0])) {
          patchState(store, { selectedEntities: store.selectedEntities().concat(foundEntity) });
        }
      },
      setCurrentEntity: (id: string | undefined): void => {
        const foundEntity = id != null && store.entities().length > 0 ? store.entities().filter((entity) => entity.id === id) : undefined;
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
