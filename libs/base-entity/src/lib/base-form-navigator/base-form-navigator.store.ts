import { patchState, signalStoreFeature, withHooks, withMethods, withState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BaseUrlSegments } from './base-url-segments';

export enum RouteSegments {
  LIST_ROUTE = 'LIST_ROUTE',
  DETAILS_ROUTE = 'DETAILS_ROUTE',
}

export interface NavigationState {
  activeRouteSegment: RouteSegments;
  entityName: string;
  navigationError?: string;
  navigateTo: string;
  returnTo: string;
}

const INITIAL_NAVIGATION_STATE: NavigationState = {
  activeRouteSegment: RouteSegments.LIST_ROUTE,
  entityName: '',
  navigateTo: '',
  returnTo: '',
};

function snakeCaseName(entityName: string) {
  return entityName
    .split(/(?=[A-Z])/)
    .join('-')
    .toLowerCase();
}

export function BaseFormNavigatorStore(entityName: string) {
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

      async function navigateBack(defaultUrl?: string): Promise<void> {
        const goTo = store.returnTo() ? store.returnTo() : defaultUrl;
        patchState(store, { returnTo: '' });
        if (goTo) {
          router
            .navigateByUrl(goTo)
            .then()
            .catch((error) => patchState(store, { navigationError: error.message }));
        } else await navigateToList();
      }

      async function navigateToDetails(id: string, returnTo?: string) {
        if (store.activeRouteSegment() != RouteSegments.DETAILS_ROUTE) {
          const snakeCaseEntityName = snakeCaseName(entityName);
          const baseUrl = determineBaseUrl();
          const detailsFormPath = baseUrl + '/' + snakeCaseEntityName + '/' + id + '/details';
          await navigateToUrl(detailsFormPath, returnTo);
        }
      }

      async function navigateToList(returnTo?: string) {
        const snakeCaseEntityName = snakeCaseName(entityName);
        const baseUrl = determineBaseUrl();
        const goToUrl = baseUrl + '/' + snakeCaseEntityName + '/list';
        if (store.activeRouteSegment() != RouteSegments.LIST_ROUTE) {
          await navigateToUrl(goToUrl, returnTo);
        }
      }

      async function navigateToRelated(relatedTypeName: string, id: string, returnTo?: string) {
        const snakeCaseEntityName = snakeCaseName(relatedTypeName);
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
