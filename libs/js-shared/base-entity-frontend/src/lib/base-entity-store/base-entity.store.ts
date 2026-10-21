import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';
import { patchState, signalStoreFeature, withComputed, withHooks, withMethods, withState, WritableStateSource } from '@ngrx/signals';
import { computed, InjectionToken, Signal } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { BaseEntityService } from '../base-entity-service/base-entity.service';
import { addEntity } from './addEntity';
import { deleteEntity } from './deleteEntity';
import { deleteAllEntities } from './deleteAllEntities';
import { updateEntity } from './updateEntity';
import { reloadEntity } from './reloadEntity';
import { stageEntity } from './stageEntity';
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
  /** Keys of rows changed in this store but not yet persisted — see {@link BaseEntityStoreApi.stage}. */
  stagedIds: string[];
}

export type EntityStoreHandle<Entity extends BaseEntity> = WritableStateSource<EntityStoreState<Entity>> & {
  entities: Signal<Entity[]>;
  selectedEntities: Signal<Entity[]>;
  stagedIds: Signal<string[]>;
};

export interface BaseEntityStoreApi<Entity extends BaseEntity> {
  entities: Signal<Entity[]>;
  selectedEntities: Signal<Entity[]>;
  stagedIds: Signal<string[]>;
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
  /**
   * Re-reads one row from the repository — after something other than this store changed it on the server.
   * Also how staged changes are discarded: the row is replaced by what the server holds.
   */
  reload(id: string): Promise<PersistedEntity<Entity> | undefined>;
  resetErrorState(): void;
  selectEntity(id: string): void;
  setCurrentEntity(id: string | undefined): void;
  /**
   * Replaces a row in this store **without** persisting it, and remembers that it is unsaved. An embedded
   * child's Save lands here: its row lives inside this entity's document, which is only written when the
   * document's own Save runs — so a rule spanning the document (an order's total against its lines) judges
   * the finished document, not each step towards it. `update` persists and clears the mark; `reload` discards.
   */
  stage(entity: PersistedEntity<Entity>): void;
  hasStagedChanges(id: string): boolean;
  update(entity: PersistedEntity<Entity>): Promise<PersistedEntity<Entity> | undefined>;
  clearCurrentEntity(): void;
  createEntity(): Entity;
  doFilter(filterKey: string): void;
  reset(): void;
  tabIsActive(tabName: string): void;
  tabIsInactive(tabName: string): void;
}

/**
 * What identifies a row of this store, as it appears in a URL segment.
 *
 * Defaults to {@link entityKeyById}, and every entity persisted through an endpoint of its own keeps
 * that default. An **embedded** entity may be keyed by something else — `App Region` has no `id` at all
 * and is identified by `type` — and its store is built with a resolver reading the `referenceIdField`
 * its owner's attribute declares. See `EmbeddedEntityFacade`.
 */
export type EntityKeyResolver<Entity extends BaseEntity> = (entity: Entity) => string | undefined;

export const entityKeyById = <Entity extends BaseEntity>(entity: Entity): string | undefined => entity.id;

export function BaseEntityStore<Entity extends BaseEntity>(entityType: new () => Entity, getRepository: () => BaseEntityService<Entity>, keyOf: EntityKeyResolver<Entity> = entityKeyById) {
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
      stagedIds: [],
    }),
    withMethods((store, repository = getRepository()) => {
      /** The row asked for as the current one that the loaded rows did not hold, until it is found or given up on. */
      let requestedId: string | undefined;
      /** The row being fetched on its own, so asking for it again while it is on its way does not fetch it twice. */
      let fetchingId: string | undefined;
      const reload = reloadEntity(store, repository, keyOf);

      /**
       * Makes the row asked for current once it can be found. The rows may have arrived since it was asked for, so they
       * are looked through first; if it is still not among them it is on a page the list did not bring (the 21st
       * workflow, say) and is asked for on its own. Without this a row beyond the first page could not be opened by its
       * link, because the screen looked for it among the rows the list had brought.
       *
       * Runs when the row is asked for, if no list is on its way, and when a list arrives, if one was.
       */
      async function resolveRequested(): Promise<void> {
        const id = requestedId;
        if (id === undefined) return;
        const found = store.entities().find((entity) => keyOf(entity) === id);
        if (!found && fetchingId === id) return; // already on its way: the screen asks again once the list has arrived
        requestedId = undefined;
        let row = found;
        if (!row) {
          fetchingId = id;
          try {
            row = await reload(id);
          } finally {
            fetchingId = undefined;
          }
        }
        // Somebody may have asked for another row while this one was on its way: theirs stands.
        const stillWanted = requestedId === undefined || requestedId === id;
        if (row && stillWanted && store.currentId() === undefined) patchState(store, { currentEntity: row, currentId: keyOf(row) });
        if (requestedId === id) requestedId = undefined;
      }

      return {
        clearCurrentEntity: () => patchState(store, { currentEntity: undefined }),
        createEntity: (): Entity => new entityType(),
        add: addEntity(store, repository),
        delete: deleteEntity(store, repository, keyOf),
        hasStagedChanges: (id: string): boolean => store.stagedIds().includes(id),
        deleteAll: deleteAllEntities(store, repository),
        deselectAll: () => {
          patchState(store, { selectedEntities: [] });
        },
        deselectEntity: (id: string) => {
          const subjectEntity = store.selectedEntities().find((entity) => keyOf(entity) === id);
          if (subjectEntity) {
            const index = store.selectedEntities().indexOf(subjectEntity, 0);
            if (index > -1) {
              const selectedEntities = store.selectedEntities().concat([]);
              selectedEntities.splice(index, 1);
              patchState(store, { selectedEntities });
            }
          }
        },
        load: findByQuery(store, repository, () => void resolveRequested()),
        loadById: (id: string): Entity | undefined => {
          return store.entities().find((entity) => keyOf(entity) === id);
        },
        reload,
        resetErrorState: () => patchState(store, { error: undefined }),
        selectEntity: (id: string) => {
          const foundEntity = store.entities().length > 0 ? store.entities().filter((entity) => keyOf(entity) === id) : undefined;
          if (foundEntity?.length === 1 && !store.selectedEntities().includes(foundEntity[0])) {
            patchState(store, { selectedEntities: store.selectedEntities().concat(foundEntity) });
          }
        },
        setCurrentEntity: (id: string | undefined): void => {
          requestedId = undefined;
          const foundEntity = id != null && store.entities().length > 0 ? store.entities().filter((entity) => keyOf(entity) === id) : undefined;
          if (foundEntity && foundEntity.length > 0) {
            patchState(store, { currentEntity: foundEntity[0], currentId: keyOf(foundEntity[0]) });
            return;
          }
          patchState(store, { currentEntity: undefined, currentId: undefined });
          // A row keyed by something other than `id` lives inside its owner's document: there is no endpoint to ask.
          if (id == null || keyOf !== entityKeyById) return;
          requestedId = id;
          // With a list on its way the row may be in it: the list resolves the request when it arrives.
          if (!store.isLoading()) void resolveRequested();
        },
        stage: stageEntity(store, keyOf),
        update: updateEntity(store, repository, keyOf),
      };
    }),
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
