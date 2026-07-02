import { patchState, signalStore, signalStoreFeature, withHooks, withMethods, withProps, withState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { BaseUrlSegments } from './base-url-segments';
import { type NavigationPayload } from './navigation-payload';
import { withDevtools } from '@angular-architects/ngrx-toolkit';

export enum RouteSegments {
  LIST_ROUTE = 'LIST_ROUTE',
  DETAILS_ROUTE = 'DETAILS_ROUTE',
}

const ROOT_PAYLOAD_KEY = '';

export interface NavigationState {
  activeRouteSegment: RouteSegments | undefined;
  entityName: string;
  navigationError?: string;
  navigateTo: string;
  responsePayloads: Map<string, NavigationPayload>;
  requestPayloads: Map<string, NavigationPayload>;
  returnTo: string;
  formSnapshot: Record<string, unknown> | undefined;
}

const INITIAL_NAVIGATION_STATE: NavigationState = {
  activeRouteSegment: RouteSegments.LIST_ROUTE,
  entityName: '',
  navigationError: undefined,
  navigateTo: '',
  responsePayloads: new Map<string, NavigationPayload>(),
  requestPayloads: new Map<string, NavigationPayload>(),
  returnTo: '',
  formSnapshot: undefined,
};

export function snakeCaseName(entityName: string) {
  return entityName
    .replace(/\s+/g, '') // strip whitespace
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1-$2') // "ITVariant" -> "IT-Variant"
    .replace(/([a-z\d])([A-Z])/g, '$1-$2') // "DeviceType" -> "Device-Type"
    .toLowerCase();
}

function payloadKey(payload: NavigationPayload): string {
  return payload.attrName ?? ROOT_PAYLOAD_KEY;
}

function clonePayloads(source: Map<string, NavigationPayload>): Map<string, NavigationPayload> {
  return new Map<string, NavigationPayload>(source);
}

function setLast(target: Map<string, NavigationPayload>, key: string, payload: NavigationPayload): void {
  target.delete(key);
  target.set(key, payload);
}

function levelUpUrl(currentUrl: string): string {
  return currentUrl.substring(0, currentUrl.lastIndexOf('/'));
}

function normalizeUrl(url: string): string {
  return url.startsWith('/') ? url : '/' + url;
}

function lastKeyOf(target: Map<string, NavigationPayload>): string | undefined {
  let last: string | undefined;
  for (const key of target.keys()) last = key;
  return last;
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
        requestPayloads: new Map<string, NavigationPayload>(),
        responsePayloads: new Map<string, NavigationPayload>(),
        formSnapshot: undefined,
      });
    }

    function captureFormSnapshot(snapshot: Record<string, unknown>): void {
      patchState(store, { formSnapshot: snapshot });
    }

    function popFormSnapshot(): Record<string, unknown> | undefined {
      const snapshot = store.formSnapshot();
      patchState(store, { formSnapshot: undefined });
      return snapshot;
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
      const requestPayloads = clonePayloads(store.requestPayloads());
      const lastKey = lastKeyOf(requestPayloads);
      if (lastKey !== undefined) requestPayloads.delete(lastKey);
      patchState(store, { requestPayloads, returnTo: '' });
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
      if (store.activeRouteSegment() !== RouteSegments.DETAILS_ROUTE) {
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
      if (store.activeRouteSegment() !== RouteSegments.LIST_ROUTE) {
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
      await navigateToUrl(listPath, returnTo);
    }

    function setEntityName(entityName: string): void {
      patchState(store, { entityName });
    }

    function pushPayload(payload?: NavigationPayload): void {
      if (!payload) return;
      const requestPayloads = clonePayloads(store.requestPayloads());
      setLast(requestPayloads, payloadKey(payload), payload);
      patchState(store, { requestPayloads });
    }

    function popRequestPayload(attrName?: string): NavigationPayload | undefined {
      const requestPayloads = clonePayloads(store.requestPayloads());
      const key = attrName ?? lastKeyOf(requestPayloads);
      if (key === undefined || !requestPayloads.has(key)) return undefined;
      const payload = requestPayloads.get(key);
      requestPayloads.delete(key);
      patchState(store, { requestPayloads });
      return payload;
    }

    function popResponsePayload(attrName?: string): NavigationPayload | undefined {
      const responsePayloads = clonePayloads(store.responsePayloads());
      const key = attrName ?? lastKeyOf(responsePayloads);
      if (key === undefined || !responsePayloads.has(key)) return undefined;
      const payload = responsePayloads.get(key);
      responsePayloads.delete(key);
      patchState(store, { responsePayloads });
      return payload;
    }

    function pushResponsePayload(payload: NavigationPayload): void {
      const responsePayloads = clonePayloads(store.responsePayloads());
      setLast(responsePayloads, payloadKey(payload), payload);
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
      captureFormSnapshot,
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
      popFormSnapshot,
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
