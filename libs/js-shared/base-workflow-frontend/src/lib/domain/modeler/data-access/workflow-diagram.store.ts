import { inject } from '@angular/core';
import { patchState, signalStore, signalStoreFeature, type, withMethods } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityStore, PersistedEntity } from '@processpuzzle/base-entity';
import { httpErrorMessage } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { WorkflowDiagram } from '../models/workflow-diagram';
import { WorkflowDiagramService } from './workflow-diagram.service';

/** The slice of `BaseEntityStore`'s state that {@link WorkflowDiagramLayoutStore} reads and patches. */
type LayoutState = {
  entities: WorkflowDiagram[];
  currentEntity: WorkflowDiagram | undefined;
  currentId: string | undefined;
  isLoading: boolean;
  error: string | undefined;
};

/**
 * Adds the two gestures the modeler actually makes — open a workflow's layout, save the arrangement — on top
 * of the generic CRUD store, keeping `addEntity` / `updateEntity`'s contract: flip `isLoading`, reconcile the
 * loaded list with the server's answer, and surface failures through `error` rather than by throwing.
 *
 * The same shape as base-state's `DiagramLayoutStore`, and they exist as methods of their own rather than as
 * uses of the inherited `loadById` / `add` / `update` for the same two reasons:
 *
 * - `loadById` only searches the already-loaded list, and the modeler opens one workflow directly by id —
 *   typically without having listed the organization's layouts at all.
 * - the choice between `add` and `update` is exactly what the contract's upsert removes; making the canvas
 *   decide would reintroduce the GET-then-decide round trip and the race on first save.
 *
 * A workflow with no layout is the normal starting point, so {@link WorkflowDiagramLayoutStore.loadLayout}
 * answering `undefined` leaves `error` clear and simply means "keep the automatic layout".
 */
export function WorkflowDiagramLayoutStore() {
  return signalStoreFeature(
    { state: type<LayoutState>() },
    withMethods((store, service = inject(WorkflowDiagramService)) => {
      /** Replaces the row of the same workflow id, or appends it — the list mirror of the upsert. */
      const upsert = (layout: PersistedEntity<WorkflowDiagram>): WorkflowDiagram[] => {
        const entities = [...store.entities()];
        const indexToUpdate = entities.findIndex((entity) => entity.id === layout.id);
        if (indexToUpdate >= 0) entities[indexToUpdate] = layout;
        else entities.push(layout);
        return entities;
      };

      return {
        /**
         * Loads the layout of one workflow and makes it current. Answers `undefined` — and leaves
         * `currentEntity` cleared — when that workflow has never been arranged.
         */
        loadLayout: async (workflowId: string): Promise<PersistedEntity<WorkflowDiagram> | undefined> => {
          patchState(store, { isLoading: true, error: undefined });
          try {
            const layout = await firstValueFrom(service.findByWorkflowId(workflowId));
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
         * Persists an arrangement, whether or not this workflow has been arranged before. The server's answer
         * replaces the layout in the store, so the `version` a later save is optimistic-locked on is the one
         * the server just assigned.
         */
        saveLayout: async (layout: WorkflowDiagram): Promise<PersistedEntity<WorkflowDiagram> | undefined> => {
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
 * The layouts of the organization's workflow diagrams.
 *
 * `BaseEntityTabsStore` / `BaseEntityContainerStore` are deliberately absent, unlike `WorkflowStore`: a
 * layout is not authored in a generated form — it has no descriptor and no tabs — it is produced by dragging
 * on the canvas. What it needs from the generic store is the loaded list, `isLoading` and `error`; the rest of
 * the CRUD surface stays available for the modeler's "re-arrange from scratch" gesture, which is the
 * inherited `delete`.
 */
export const WorkflowDiagramStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<WorkflowDiagram>(WorkflowDiagram, () => inject(WorkflowDiagramService)),
  WorkflowDiagramLayoutStore(),
  withDevtools('WorkflowDiagram'),
);
