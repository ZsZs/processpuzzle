import { inject } from '@angular/core';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { patchState, signalStore, signalStoreFeature, type, withMethods } from '@ngrx/signals';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore, PersistedEntity } from '@processpuzzle/base-entity';
import { httpErrorMessage } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { WidgetDefinition } from './widget-definition';
import { WidgetDefinitionService } from './widget-definition.service';

/** The slice of `BaseEntityStore`'s state that {@link WidgetDefinitionPublishStore} reads and patches. */
type PublishableState = {
  entities: WidgetDefinition[];
  currentEntity: WidgetDefinition | undefined;
  isLoading: boolean;
  error: string | undefined;
};

/**
 * Adds the `publish` use case on top of the generic CRUD store, with the same contract as
 * `AppDefinitionPublishStore`: flip `isLoading`, replace the entity in the list with the server's answer so
 * the form's read-only `status` / `publishedVersion` refresh, and surface failures through `error` rather
 * than by throwing.
 *
 * A widget type is published in its own right, unlike a module — its props schema is a contract with every
 * instance that names it, so "which version of it is live" is a question about the widget alone.
 */
export function WidgetDefinitionPublishStore() {
  return signalStoreFeature(
    { state: type<PublishableState>() },
    withMethods((store, service = inject(WidgetDefinitionService)) => ({
      publish: async (id: string): Promise<PersistedEntity<WidgetDefinition> | undefined> => {
        patchState(store, { isLoading: true, error: undefined });
        try {
          const publishedEntity = await firstValueFrom(service.publish(id));
          const entities = [...store.entities()];
          const indexToUpdate = entities.findIndex((entity) => entity.id === id);
          if (indexToUpdate >= 0) entities[indexToUpdate] = publishedEntity;
          const currentEntity = store.currentEntity()?.id === id ? publishedEntity : store.currentEntity();
          patchState(store, { entities, currentEntity, isLoading: false });
          return publishedEntity;
        } catch (error) {
          patchState(store, { error: httpErrorMessage(error), isLoading: false });
          return undefined;
        }
      },
    })),
  );
}

export const WidgetDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<WidgetDefinition>(WidgetDefinition, () => inject(WidgetDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  WidgetDefinitionPublishStore(),
  withDevtools('WidgetDefinition'),
);
