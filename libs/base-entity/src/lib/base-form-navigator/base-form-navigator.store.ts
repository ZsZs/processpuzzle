import { patchState, signalStore, signalStoreFeature, withHooks, withMethods, withProps, withState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Stack } from '@processpuzzle/util';
import { BaseUrlSegments } from './base-url-segments';
import { type NavigationPayload, NavigatorCommand } from './navigation-payload';
import { withDevtools } from '@angular-architects/ngrx-toolkit';

export enum RouteSegments {
  LIST_ROUTE = 'LIST_ROUTE',
  DETAILS_ROUTE = 'DETAILS_ROUTE',
}

export interface NavigationState {
  activeRouteSegment: RouteSegments | undefined;
  entityName: string;
  navigationError?: string;
  navigateTo: string;
  responsePayloads: Stack<NavigationPayload>;
  requestPayloads: Stack<NavigationPayload>;
  returnTo: string;
}

const INITIAL_NAVIGATION_STATE: NavigationState = {
  activeRouteSegment: RouteSegments.LIST_ROUTE,
  entityName: '',
  navigationError: undefined,
  navigateTo: '',
  responsePayloads: new Stack<NavigationPayload>(),
  requestPayloads: new Stack<NavigationPayload>(),
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
  withDevtools('Base Form Navigator'),
  withMethods((store, router = inject(Router), route = inject(ActivatedRoute)) => {
    let pendingNavigatorUrl: string | undefined;
    let routerEventsSubscription: { unsubscribe(): void } | undefined;

    function clearPayloadStacks(): void {
      patchState(store, {
        requestPayloads: new Stack<NavigationPayload>(),
        responsePayloads: new Stack<NavigationPayload>(),
      });
    }

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

    function normalizeUrl(url: string): string {
      return url.startsWith('/') ? url : '/' + url;
    }

    function isPendingNavigatorUrl(navigationEnd: NavigationEnd): boolean {
      if (!pendingNavigatorUrl) {
        return false;
      }

      const normalizedPendingUrl = normalizeUrl(pendingNavigatorUrl);
      return [navigationEnd.url, navigationEnd.urlAfterRedirects].some((url) => normalizeUrl(url) === normalizedPendingUrl);
    }

    function initializeNavigationTracking(): void {
      routerEventsSubscription ??= router.events.subscribe((event) => {
        if (!(event instanceof NavigationEnd)) {
          return;
        }

        // eslint-disable-next-line no-console
        console.debug('[FormNavigator] NavigationEnd', {
          url: event.url,
          urlAfterRedirects: event.urlAfterRedirects,
          pendingNavigatorUrl,
          isPending: isPendingNavigatorUrl(event),
          requestPayloads: store.requestPayloads().toArray(),
        });

        if (isPendingNavigatorUrl(event)) {
          pendingNavigatorUrl = undefined;
        } else {
          clearPayloadStacks();
        }

        determineActiveRouteSegment();
      });
    }

    function destroyNavigationTracking(): void {
      routerEventsSubscription?.unsubscribe();
      routerEventsSubscription = undefined;
    }

    async function navigateBack(defaultUrl?: string): Promise<void> {
      const goTo = store.returnTo() ? store.returnTo() : defaultUrl;
      const navigatorPayloads = new Stack<NavigationPayload>(store.requestPayloads().toArray());
      navigatorPayloads.pop();
      patchState(store, { requestPayloads: navigatorPayloads, returnTo: '' });
      if (goTo) {
        pendingNavigatorUrl = goTo;
        await router
          .navigateByUrl(goTo)
          .then()
          .catch((error) => {
            pendingNavigatorUrl = undefined;
            patchState(store, { navigationError: error.message });
          });
      } else await navigateToList(store.entityName());
    }

    async function navigateToDetails(entityName: string, id: string, returnTo?: string, payload?: NavigationPayload) {
      patchState(store, { entityName });
      pushPayload(payload);
      if (store.activeRouteSegment() != RouteSegments.DETAILS_ROUTE) {
        const snakeCaseEntityName = snakeCaseName(entityName);
        const baseUrl = determineBaseUrl();
        const detailsFormPath = baseUrl + '/' + snakeCaseEntityName + '/' + id + '/details';
        await navigateToUrl(detailsFormPath, returnTo);
      }
    }

    async function navigateToList(entityName: string, returnTo?: string, payload?: NavigationPayload) {
      patchState(store, { entityName });
      pushPayload(payload);
      const snakeCaseEntityName = snakeCaseName(entityName);
      const baseUrl = determineBaseUrl();
      const goToUrl = baseUrl + '/' + snakeCaseEntityName + '/list';
      if (store.activeRouteSegment() != RouteSegments.LIST_ROUTE) {
        await navigateToUrl(goToUrl, returnTo);
      }
    }

    async function navigateToRelated(relatedTypeName: string, id: string, returnTo?: string, payload?: NavigationPayload) {
      patchState(store, { entityName: relatedTypeName });
      pushPayload(payload);
      const snakeCaseEntityName = snakeCaseName(relatedTypeName);
      const baseUrl = determineBaseUrl();
      const detailsFormPath = baseUrl + '/' + snakeCaseEntityName + '/' + id + '/details';
      await navigateToUrl(detailsFormPath, returnTo);
    }

    async function navigateToRelatedList(relatedTypeName: string, returnTo?: string, payload?: NavigationPayload) {
      patchState(store, { entityName: relatedTypeName });
      pushPayload(payload);
      const snakeCaseEntityName = snakeCaseName(relatedTypeName);
      const baseUrl = determineBaseUrl();
      const listPath = baseUrl + '/' + snakeCaseEntityName + '/list';
      // eslint-disable-next-line no-console
      console.debug('[FormNavigator] navigateToRelatedList', { relatedTypeName, listPath, payload, requestPayloadsAfterPush: store.requestPayloads().toArray() });
      await navigateToUrl(listPath, returnTo);
    }

    function setEntityName(entityName: string): void {
      patchState(store, { entityName });
    }

    function pushPayload(payload?: NavigationPayload): void {
      if (payload) {
        const navigatorPayloads = new Stack<NavigationPayload>(store.requestPayloads().toArray());
        navigatorPayloads.push(payload);
        patchState(store, { requestPayloads: navigatorPayloads });
      }
    }

    function popRequestPayload(): NavigationPayload | undefined {
      const navigatorPayloads = new Stack<NavigationPayload>(store.requestPayloads().toArray());
      const payload = navigatorPayloads.pop();
      patchState(store, { requestPayloads: navigatorPayloads });
      return payload;
    }

    function popResponsePayload(command?: NavigatorCommand): NavigationPayload | undefined {
      const responsePayloadArray = store.responsePayloads().toArray();
      const payloadIndex = command === undefined ? responsePayloadArray.length - 1 : responsePayloadArray.map((payload) => payload.command).lastIndexOf(command);

      if (payloadIndex < 0) {
        return undefined;
      }

      const [payload] = responsePayloadArray.splice(payloadIndex, 1);
      patchState(store, { responsePayloads: new Stack<NavigationPayload>(responsePayloadArray) });
      return payload;
    }

    function pushResponsePayload(payload: NavigationPayload): void {
      const responsePayloads = new Stack<NavigationPayload>(store.responsePayloads().toArray());
      responsePayloads.push(payload);
      patchState(store, { responsePayloads });
    }

    async function navigateToUrl(url: string, returnTo?: string) {
      if (returnTo) {
        patchState(store, { returnTo });
      } else {
        patchState(store, { returnTo: router.url });
      }
      pendingNavigatorUrl = url;
      patchState(store, { navigateTo: url });
      await router
        .navigateByUrl(url)
        .then(() => determineActiveRouteSegment())
        .catch((error) => {
          pendingNavigatorUrl = undefined;
          patchState(store, { navigationError: error.message });
        });
    }

    return {
      determineCurrentUrl,
      determineActiveRouteSegment,
      navigateBack,
      navigateToDetails,
      navigateToList,
      navigateToRelated,
      navigateToRelatedList,
      navigateToUrl,
      destroyNavigationTracking,
      initializeNavigationTracking,
      popRequestPayload,
      popResponsePayload,
      pushResponsePayload,
      setEntityName,
    };
  }),
  withHooks((store) => ({
    onInit: () => {
      store.determineActiveRouteSegment();
      store.initializeNavigationTracking();
    },
    onDestroy: () => {
      store.destroyNavigationTracking();
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
        navigatorPayloads: navigatorStore.requestPayloads,
        requestPayloads: navigatorStore.requestPayloads,
        responsePayloads: navigatorStore.responsePayloads,
        returnTo: navigatorStore.returnTo,
        determineActiveRouteSegment: navigatorStore.determineActiveRouteSegment,
        determineCurrentUrl: navigatorStore.determineCurrentUrl,
        navigateBack: navigatorStore.navigateBack,
        navigateToDetails: (id: string, returnTo?: string, payload?: NavigationPayload) => navigatorStore.navigateToDetails(entityName, id, returnTo, payload),
        navigateToList: (returnTo?: string, payload?: NavigationPayload) => navigatorStore.navigateToList(entityName, returnTo, payload),
        navigateToRelated: navigatorStore.navigateToRelated,
        navigateToRelatedList: navigatorStore.navigateToRelatedList,
        navigateToUrl: navigatorStore.navigateToUrl,
        popRequestPayload: navigatorStore.popRequestPayload,
        popResponsePayload: navigatorStore.popResponsePayload,
        pushResponsePayload: navigatorStore.pushResponsePayload,
      };
    }),
  );
}
