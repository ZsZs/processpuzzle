import { patchState, signalStoreFeature, withHooks, withMethods, withState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BaseUrlSegments } from './base-url-segments';
import { BaseEntity } from '../base-entity/base-entity';

export enum RouteSegments {
  LIST_ROUTE = 'LIST_ROUTE',
  DETAILS_ROUTE = 'DETAILS_ROUTE',
}
export interface NavigationState {
  activeRouteSegment: RouteSegments;
  navigationError?: string;
  navigateTo: string;
  returnTo: string;
}

const INITIAL_NAVIGATION_STATE: NavigationState = {
  activeRouteSegment: RouteSegments.LIST_ROUTE,
  navigateTo: '',
  returnTo: '',
};

function entityName<Entity extends BaseEntity>(entityType: { new (): Entity }) {
  const entity = new entityType();
  return entity.constructor.name;
}

function snakeCaseName<Entity extends BaseEntity>(entityType: { new (): Entity }) {
  const nameOfEntity = entityName(entityType);
  return nameOfEntity
    .split(/(?=[A-Z])/)
    .join('-')
    .toLowerCase();
}

export function BaseFormNavigatorStore<Entity extends BaseEntity>(entityType: { new (): Entity }) {
  return signalStoreFeature(
    withState<NavigationState>(INITIAL_NAVIGATION_STATE),
    withMethods((store, router = inject(Router), route = inject(ActivatedRoute)) => {
      function determineActiveRouteSegment(): void {
        const currentUrl = Reflect.get(route, '_routerState').snapshot.url;
        if (currentUrl.endsWith(BaseUrlSegments.ListForm)) {
          patchState(store, { activeRouteSegment: RouteSegments.LIST_ROUTE });
        } else if (currentUrl.endsWith(BaseUrlSegments.DetailsForm)) {
          patchState(store, { activeRouteSegment: RouteSegments.DETAILS_ROUTE });
        } else {
          patchState(store, { activeRouteSegment: undefined });
        }
      }
      function determineBaseUrl(): string {
        determineActiveRouteSegment();
        const currentUrl = determineCurrentUrl();
        return store.activeRouteSegment() === RouteSegments.DETAILS_ROUTE ? levelUpUrl(levelUpUrl(levelUpUrl(currentUrl))) : levelUpUrl(levelUpUrl(currentUrl));
      }
      function determineCurrentUrl(): string {
        return Reflect.get(route, '_routerState').snapshot.url;
      }
      function levelUpUrl(currentUrl: string) {
        return currentUrl.substring(0, currentUrl.lastIndexOf('/'));
      }
      function navigateBack(defaultUrl?: string): void {
        const goTo = store.returnTo() ? store.returnTo() : defaultUrl;
        patchState(store, { returnTo: '' });
        if (goTo) {
          router
            .navigateByUrl(goTo)
            .then()
            .catch((error) => patchState(store, { navigationError: error.message }));
        } else navigateToList();
      }
      async function navigateToDetails(id: string, returnTo?: string) {
        if (store.activeRouteSegment() != RouteSegments.DETAILS_ROUTE) {
          const snakeCaseEntityName = snakeCaseName<Entity>(entityType);
          const baseUrl = determineBaseUrl();
          const detailsFormPath = baseUrl + '/' + snakeCaseEntityName + '/' + id + '/details';
          await navigateToUrl(detailsFormPath, returnTo);
        }
      }
      async function navigateToList(returnTo?: string) {
        const snakeCaseEntityName = snakeCaseName<Entity>(entityType);
        const baseUrl = determineBaseUrl();
        const goToUrl = baseUrl + '/' + snakeCaseEntityName + '/list';
        if (store.activeRouteSegment() != RouteSegments.LIST_ROUTE) {
          await navigateToUrl(goToUrl, returnTo);
        }
      }
      async function navigateToRelated(relatedType: { new (): Entity }, id: string, returnTo?: string) {
        const snakeCaseEntityName = snakeCaseName<Entity>(relatedType);
        const baseUrl = determineBaseUrl();
        const detailsFormPath = baseUrl + '/' + snakeCaseEntityName + '/' + id + '/details';
        await navigateToUrl(detailsFormPath, returnTo);
      }
      async function navigateToUrl(url: string, returnTo?: string) {
        if (!returnTo) patchState(store, { returnTo: router.url });
        else patchState(store, { returnTo });
        patchState(store, { navigateTo: url });
        await router
          .navigateByUrl(url)
          .then(() => determineActiveRouteSegment())
          .catch((error) => patchState(store, { navigationError: error.message }));
      }
      return { determineCurrentUrl, determineActiveRouteSegment, navigateBack, navigateToDetails, navigateToList, navigateToRelated, navigateToUrl };
    }),
    withHooks((store) => ({
      onInit: () => {
        store.determineActiveRouteSegment();
      },
    })),
  );
}
