import { patchState, signalStore, signalStoreFeature, withHooks, withMethods, withProps, withState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BaseUrlSegments } from './base-url-segments';

export enum RouteSegments {
  LIST_ROUTE = 'LIST_ROUTE',
  DETAILS_ROUTE = 'DETAILS_ROUTE',
}

export interface NavigationState {
  activeRouteSegment: RouteSegments | undefined;
  entityName: string;
  navigationError?: string;
  navigateTo: string;
  returnTo: string;
}

const INITIAL_NAVIGATION_STATE: NavigationState = {
  activeRouteSegment: RouteSegments.LIST_ROUTE,
  entityName: '',
  navigationError: undefined,
  navigateTo: '',
  returnTo: '',
};

function snakeCaseName(entityName: string) {
  return entityName
    .replace(/\s+/g, '')
    .split(/(?=[A-Z])/)
    .join('-')
    .toLowerCase();
}

export const BaseFormNavigatorSingletonStore = signalStore(
  { providedIn: 'root' },
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
        await router
          .navigateByUrl(goTo)
          .then()
          .catch((error) => patchState(store, { navigationError: error.message }));
      } else await navigateToList(store.entityName());
    }

    async function navigateToDetails(entityName: string, id: string, returnTo?: string) {
      patchState(store, { entityName });
      if (store.activeRouteSegment() != RouteSegments.DETAILS_ROUTE) {
        const snakeCaseEntityName = snakeCaseName(entityName);
        const baseUrl = determineBaseUrl();
        const detailsFormPath = baseUrl + '/' + snakeCaseEntityName + '/' + id + '/details';
        await navigateToUrl(detailsFormPath, returnTo);
      }
    }

    async function navigateToList(entityName: string, returnTo?: string) {
      patchState(store, { entityName });
      const snakeCaseEntityName = snakeCaseName(entityName);
      const baseUrl = determineBaseUrl();
      const goToUrl = baseUrl + '/' + snakeCaseEntityName + '/list';
      if (store.activeRouteSegment() != RouteSegments.LIST_ROUTE) {
        await navigateToUrl(goToUrl, returnTo);
      }
    }

    async function navigateToRelated(relatedTypeName: string, id: string, returnTo?: string) {
      patchState(store, { entityName: relatedTypeName });
      const snakeCaseEntityName = snakeCaseName(relatedTypeName);
      const baseUrl = determineBaseUrl();
      const detailsFormPath = baseUrl + '/' + snakeCaseEntityName + '/' + id + '/details';
      await navigateToUrl(detailsFormPath, returnTo);
    }

    async function navigateToRelatedList(relatedTypeName: string, returnTo?: string) {
      patchState(store, { entityName: relatedTypeName });
      const snakeCaseEntityName = snakeCaseName(relatedTypeName);
      const baseUrl = determineBaseUrl();
      const listPath = baseUrl + '/' + snakeCaseEntityName + '/list';
      await navigateToUrl(listPath, returnTo);
    }

    function setEntityName(entityName: string): void {
      patchState(store, { entityName });
    }

    async function navigateToUrl(url: string, returnTo?: string) {
      if (returnTo) {
        patchState(store, { returnTo });
      } else {
        patchState(store, { returnTo: router.url });
      }
      patchState(store, { navigateTo: url });
      await router
        .navigateByUrl(url)
        .then(() => determineActiveRouteSegment())
        .catch((error) => patchState(store, { navigationError: error.message }));
    }

    return { determineCurrentUrl, determineActiveRouteSegment, navigateBack, navigateToDetails, navigateToList, navigateToRelated, navigateToRelatedList, navigateToUrl, setEntityName };
  }),
  withHooks((store) => ({
    onInit: () => {
      store.determineActiveRouteSegment();
    },
  })),
);

export function BaseFormNavigatorStore(entityName: string) {
  return signalStoreFeature(
    withProps((_, navigatorStore = inject(BaseFormNavigatorSingletonStore)) => {
      navigatorStore.setEntityName(entityName);
      return {
        activeRouteSegment: navigatorStore.activeRouteSegment,
        entityName: navigatorStore.entityName,
        navigationError: navigatorStore.navigationError,
        navigateTo: navigatorStore.navigateTo,
        returnTo: navigatorStore.returnTo,
        determineActiveRouteSegment: navigatorStore.determineActiveRouteSegment,
        determineCurrentUrl: navigatorStore.determineCurrentUrl,
        navigateBack: navigatorStore.navigateBack,
        navigateToDetails: (id: string, returnTo?: string) => navigatorStore.navigateToDetails(entityName, id, returnTo),
        navigateToList: (returnTo?: string) => navigatorStore.navigateToList(entityName, returnTo),
        navigateToRelated: navigatorStore.navigateToRelated,
        navigateToRelatedList: navigatorStore.navigateToRelatedList,
        navigateToUrl: navigatorStore.navigateToUrl,
      };
    }),
  );
}
