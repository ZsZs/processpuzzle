import { inject } from '@angular/core';
import { patchState, signalStore, signalStoreFeature, type, withMethods } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityStore, PersistedEntity } from '@processpuzzle/base-entity';
import { httpErrorMessage } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { DiagramDefinition } from 'libs/js-shared/base-state-frontend/src/lib/domain/modeler/models/diagram-definition';
import { DiagramDefinitionService } from 'libs/js-shared/base-state-frontend/src/lib/domain/modeler/data-access/diagram-definition.service';

/** The slice of `BaseEntityStore`'s state that {@link DiagramLayoutStore} reads and patches. */
type LayoutState = {
  entities: DiagramDefinition[];
  currentEntity: DiagramDefinition | undefined;
  currentId: string | undefined;
  isLoading: boolean;
  error: string | undefined;
};

/**
 * Adds the two gestures the modeler actually makes — open a machine's layout, save the arrangement — on
 * top of the generic CRUD store, keeping `addEntity` / `updateEntity`'s contract: flip `isLoading`,
 * reconcile the loaded list with the server's answer, and surface failures through `error` rather than
 * by throwing.
 *
 * They exist as methods of their own rather than as uses of the inherited `loadById` / `add` / `update`
 * because a layout is addressed by `entityName` and neither inherited method fits that:
 *
 * - `loadById` only searches the already-loaded list, and the modeler opens one machine directly by
 *   name — typically without having listed the organization's layouts at all.
 * - the choice between `add` and `update` is exactly what the contract's upsert removes; making the
 *   canvas decide would reintroduce the GET-then-decide round trip and the race on first save.
 *
 * A machine with no layout is the normal starting point, so {@link DiagramLayoutStore.loadLayout}
 * answering `undefined` leaves `error` clear and simply means "fall back to an automatic layout".
 */
export function DiagramLayoutStore() {
  return signalStoreFeature(
    { state: type<LayoutState>() },
    withMethods((store, service = inject(DiagramDefinitionService)) => {
      /** Replaces the row of the same entity name, or appends it — the list mirror of the upsert. */
      const upsert = (layout: PersistedEntity<DiagramDefinition>): DiagramDefinition[] => {
        const entities = [...store.entities()];
        const indexToUpdate = entities.findIndex((entity) => entity.id === layout.id);
        if (indexToUpdate >= 0) entities[indexToUpdate] = layout;
        else entities.push(layout);
        return entities;
      };

      return {
        /**
         * Loads the layout of one entity type's state machine and makes it current. Answers `undefined`
         * — and leaves `currentEntity` cleared — when that machine has never been arranged.
         */
        loadLayout: async (entityName: string): Promise<PersistedEntity<DiagramDefinition> | undefined> => {
          patchState(store, { isLoading: true, error: undefined });
          try {
            const layout = await firstValueFrom(service.findByEntityName(entityName));
            patchState(store, {
              entities: layout ? upsert(layout) : store.entities(),
              currentEntity: layout,
              currentId: layout?.id,
              isLoading: false,
            });
            return layout;
          } catch (error) {
            patchState(store, { error: httpErrorMessage(error), isLoading: false });
            return undefined;
          }
        },

        /**
         * Persists an arrangement, whether or not this machine has been arranged before. The server's
         * answer replaces the layout in the store, so the `version` a later save is optimistic-locked
         * on is the one the server just assigned.
         */
        saveLayout: async (layout: DiagramDefinition): Promise<PersistedEntity<DiagramDefinition> | undefined> => {
          patchState(store, { isLoading: true, error: undefined });
          try {
            const savedLayout = await firstValueFrom(service.save(layout));
            patchState(store, { entities: upsert(savedLayout), currentEntity: savedLayout, currentId: savedLayout.id, isLoading: false });
            return savedLayout;
          } catch (error) {
            patchState(store, { error: httpErrorMessage(error), isLoading: false });
            return undefined;
          }
        },
      };
    }),
  );
}

/**
 * The layouts of the organization's state machines.
 *
 * `BaseEntityTabsStore` / `BaseEntityContainerStore` are deliberately absent, unlike
 * `StateMachineDefinitionStore`: a layout is not authored in a generated form — it has no descriptor and
 * no tabs — it is produced by dragging on the canvas. What it needs from the generic store is the loaded
 * list, `isLoading` and `error`; the rest of the CRUD surface stays available for the modeler's
 * "re-arrange from scratch" gesture, which is the inherited `delete`.
 */
export const DiagramDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<DiagramDefinition>(DiagramDefinition, () => inject(DiagramDefinitionService)),
  DiagramLayoutStore(),
  withDevtools('DiagramDefinition'),
);
