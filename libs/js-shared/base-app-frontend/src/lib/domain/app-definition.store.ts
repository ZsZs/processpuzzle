import { inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { patchState, signalStore, signalStoreFeature, type, withMethods } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore, PersistedEntity } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';
import { AppDefinition } from './app-definition';
import { AppDefinitionService } from './app-definition.service';

/** The slice of `BaseEntityStore`'s state that {@link AppDefinitionPublishStore} reads and patches. */
type PublishableState = {
  entities: AppDefinition[];
  currentEntity: AppDefinition | undefined;
  isLoading: boolean;
  error: string | undefined;
};

/**
 * Adds the `publish` use case on top of the generic CRUD store. It keeps the same contract as
 * `addEntity` / `updateEntity`: flip `isLoading`, replace the entity in the list with the server's
 * answer so the form's read-only `status` / `publishedVersion` fields refresh, and surface failures
 * through `error` rather than by throwing.
 */
export function AppDefinitionPublishStore() {
  return signalStoreFeature(
    { state: type<PublishableState>() },
    withMethods((store, service = inject(AppDefinitionService)) => ({
      publish: async (id: string): Promise<PersistedEntity<AppDefinition> | undefined> => {
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
          patchState(store, { error: (error as HttpErrorResponse).message, isLoading: false });
          return undefined;
        }
      },
    })),
  );
}

export const AppDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<AppDefinition>(AppDefinition, () => inject(AppDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  AppDefinitionPublishStore(),
  withDevtools('AppDefinition'),
);
