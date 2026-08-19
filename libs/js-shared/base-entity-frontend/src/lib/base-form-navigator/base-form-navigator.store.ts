import { patchState, signalStore, signalStoreFeature, withHooks, withMethods, withProps, withState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { ActivatedRoute, ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { BaseUrlSegments } from './base-url-segments';
import { type NavigationPayload } from './navigation-payload';
import { EntityRouteRegistry } from './entity-route.registry';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { type EmbeddedBreadcrumbLevel, readEmbeddedBreadcrumb } from '../base-entity-embedded/embedded-route-context';

export enum RouteSegments {
  LIST_ROUTE = 'LIST_ROUTE',
  DETAILS_ROUTE = 'DETAILS_ROUTE',
  /**
   * One of an entity's extra tabs (see `EntityTabDescriptor`) — `<entity>/<id>/content` and the like.
   * Which segments count is registered by {@link BaseEntityTabsComponent}, because the store has no
   * descriptors of its own: a URL tail is only a tab if some entity on screen declared it as one, and an
   * unregistered tail stays `undefined` exactly as before.
   */
  ENTITY_TAB_ROUTE = 'ENTITY_TAB_ROUTE',
}

const ROOT_PAYLOAD_KEY = '';

export interface NavigationState {
  activeRouteSegment: RouteSegments | undefined;
  /**
   * The extra tab the current URL ends in, when {@link activeRouteSegment} is `ENTITY_TAB_ROUTE`. Which
   * tab is open cannot be read off the enum, since every extra tab shares that one value.
   */
  activeTabSegment: string | undefined;
  /** Extra tab segments the entities currently on screen declare. See {@link RouteSegments.ENTITY_TAB_ROUTE}. */
  tabSegments: string[];
  /**
   * The entities the current URL walks through, outermost first — what the status bar renders as a
   * breadcrumb. Derived from the route, never remembered, so a deep link and a refresh describe the same
   * hierarchy as a drill-down.
   */
  breadcrumb: EmbeddedBreadcrumbLevel[];
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
  activeTabSegment: undefined,
  tabSegments: [],
  breadcrumb: [],
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
    .replace(/([A-Z]+)(?=[A-Z][a-z])/g, '$1-') // "ITVariant" -> "IT-Variant"
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

/**
 * Whether the URL's last segment is exactly `segment`, anchored on the preceding `/`.
 *
 * Unanchored — a bare `endsWith('list')` — any path whose *letters* happen to end that way read as the
 * list form: a previewed application's own `…/preview/order-list` classified as `LIST_ROUTE`, which put
 * the hosting entity's toolbar on the Preview tab.
 */
function endsWithSegment(url: string, segment: string): boolean {
  return url.endsWith('/' + segment);
}

/**
 * The registered tab segment the URL sits on **or below**, innermost first.
 *
 * Below, and not only at the end, because a tab may be a *container*: the Preview tab hosts the previewed
 * application's own screens, so `…/preview/order-list` is still the Preview tab. Read only at the end, such
 * a URL classified as nothing at all — which lit the List link up while the Preview tab was on display.
 */
function findTabSegment(url: string, tabSegments: string[]): string | undefined {
  const segments = url.split('/');
  for (let index = segments.length - 1; index > 0; index--) {
    if (tabSegments.includes(segments[index])) return segments[index];
  }
  return undefined;
}

/**
 * The prefix a tab URL is built on: everything before the entity's own `<entity>/<id>/<segment>` triple,
 * wherever that sits.
 *
 * Counting three levels back from the end is only right while the tab is a leaf. A container tab's URL
 * continues past it, and the count then leaves part of the previewed application's path in the prefix —
 * which is how the tab bar's List and Details links came to navigate to `/app-definition/demo/app-definition/demo/details`.
 */
function baseUrlOfTabRoute(currentUrl: string, tabSegment: string | undefined): string {
  const segments = currentUrl.split('/');
  const tabIndex = tabSegment ? segments.lastIndexOf(tabSegment) : -1;
  // Shorter than `<base>/<entity>/<id>/<segment>`: the entity's screens are at the root and there is no prefix.
  return tabIndex < 3 ? '' : segments.slice(0, tabIndex - 2).join('/');
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
  withMethods((store, router = inject(Router), route = inject(ActivatedRoute), entityRouteRegistry = inject(EntityRouteRegistry)) => {
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

    /** Re-derives everything that is read off the URL: which form is open, and the breadcrumb to it. */
    function determineActiveRouteSegment(): void {
      const currentUrl = Reflect.get(route, '_routerState').snapshot.url;
      const tabSegment = findTabSegment(currentUrl, store.tabSegments());
      // A list or details form the URL *ends* on wins over a tab it merely passes through: those two are the
      // innermost screen wherever they appear, including inside a container tab.
      if (endsWithSegment(currentUrl, BaseUrlSegments.ListForm)) {
        patchState(store, { activeRouteSegment: RouteSegments.LIST_ROUTE, activeTabSegment: undefined });
      } else if (endsWithSegment(currentUrl, BaseUrlSegments.DetailsForm)) {
        patchState(store, { activeRouteSegment: RouteSegments.DETAILS_ROUTE, activeTabSegment: undefined });
      } else if (tabSegment) {
        patchState(store, { activeRouteSegment: RouteSegments.ENTITY_TAB_ROUTE, activeTabSegment: tabSegment });
      } else {
        patchState(store, { activeRouteSegment: undefined, activeTabSegment: undefined });
      }
      patchState(store, { breadcrumb: readEmbeddedBreadcrumb(deepestActivatedRoute()) });
    }

    /**
     * Extra tab segments to recognize from here on, added to whatever is already registered — several
     * entities can be on screen at once (an embedded child below its owner), and the outer one's tabs must
     * not disappear when the inner one registers none.
     */
    function registerTabSegments(segments: string[]): void {
      const known = store.tabSegments();
      const merged = segments.filter((segment) => !known.includes(segment));
      if (merged.length === 0) return;
      patchState(store, { tabSegments: known.concat(merged) });
      // The URL may already be on one of the segments just registered — a reload straight onto
      // `<entity>/<id>/content` classifies before any tab is known, and would otherwise stay `undefined`.
      determineActiveRouteSegment();
    }

    function deepestActivatedRoute(): ActivatedRouteSnapshot {
      let snapshot = router.routerState.snapshot.root;
      while (snapshot.firstChild) snapshot = snapshot.firstChild;
      return snapshot;
    }

    /**
     * The prefix a `<entity>/...` URL is built on.
     *
     * Taken from the breadcrumb when the entity is one the current URL walks through — which is what keeps
     * the answer right at any depth, where counting segments up from the end is not: from an embedded
     * child's form, the owner's list is not three levels up. Falls back to that count for an entity the
     * URL says nothing about.
     */
    function determineBaseUrl(entityName?: string): string {
      determineActiveRouteSegment();
      const level = store.breadcrumb().find((candidate) => candidate.entityName === entityName);
      if (level) return level.baseUrl;

      const currentUrl = determineCurrentUrl();
      // An extra tab's URL has the details route's shape — `<base>/<entity>/<id>/<segment>` — but it does
      // not have to *end* there: a container tab carries the screens it hosts below it. So the triple is
      // located rather than counted back from the end.
      if (store.activeRouteSegment() === RouteSegments.ENTITY_TAB_ROUTE) return baseUrlOfTabRoute(currentUrl, store.activeTabSegment());
      // The details form is always the end of its URL, so counting back over its own three levels holds.
      // Reading it as the two-level list shape would leave one segment of the entity's own path in the
      // prefix, and every URL built on it would be wrong.
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

    /**
     * Walks the breadcrumb back up to `index`.
     *
     * Deliberately without a `returnTo`: going *up* means the form landed on should fall back to its own
     * back target — the owner's form for an embedded level, its own list for the root — rather than to the
     * level the user just left, which would make Cancel come straight back down.
     */
    async function navigateToBreadcrumbLevel(index: number): Promise<void> {
      const level = store.breadcrumb()[index];
      if (!level) return;

      patchState(store, { entityName: level.entityName, returnTo: '' });
      await navigate(level.url);
    }

    async function navigateToDetails(entityName: string, id: string, returnTo?: string, payload?: NavigationPayload) {
      patchState(store, { entityName });
      pushPayload(payload);
      if (store.activeRouteSegment() !== RouteSegments.DETAILS_ROUTE) {
        const snakeCaseEntityName = snakeCaseName(entityName);
        const baseUrl = determineBaseUrl(entityName);
        const detailsFormPath = baseUrl + '/' + snakeCaseEntityName + '/' + id + '/details';
        await navigateToUrl(detailsFormPath, returnTo);
      }
    }

    /**
     * One of the entity's extra tabs, built on the same prefix as its details form so both tabs of one row
     * address the same record. Guarded against re-navigating to the tab already open, as
     * {@link navigateToDetails} is — clicking the active link should be inert, not a reload.
     */
    async function navigateToTab(entityName: string, id: string, segment: string, returnTo?: string, payload?: NavigationPayload) {
      patchState(store, { entityName });
      pushPayload(payload);
      if (store.activeRouteSegment() === RouteSegments.ENTITY_TAB_ROUTE && store.activeTabSegment() === segment) return;

      const baseUrl = determineBaseUrl(entityName);
      await navigateToUrl(`${baseUrl}/${snakeCaseName(entityName)}/${id}/${segment}`, returnTo);
    }

    async function navigateToList(entityName: string, returnTo?: string, payload?: NavigationPayload) {
      patchState(store, { entityName });
      pushPayload(payload);
      const snakeCaseEntityName = snakeCaseName(entityName);
      const baseUrl = determineBaseUrl(entityName);
      const goToUrl = baseUrl + '/' + snakeCaseEntityName + '/list';
      if (store.activeRouteSegment() !== RouteSegments.LIST_ROUTE) {
        await navigateToUrl(goToUrl, returnTo);
      }
    }

    async function navigateToRelated(relatedTypeName: string, id: string, returnTo?: string, payload?: NavigationPayload) {
      patchState(store, { entityName: relatedTypeName });
      pushPayload(payload);
      const registeredPath = entityRouteRegistry.detailsPath(relatedTypeName, id);
      if (registeredPath) {
        await navigateToUrl(registeredPath, returnTo);
        return;
      }
      const snakeCaseEntityName = snakeCaseName(relatedTypeName);
      const baseUrl = determineBaseUrl();
      const detailsFormPath = baseUrl + '/' + snakeCaseEntityName + '/' + id + '/details';
      await navigateToUrl(detailsFormPath, returnTo);
    }

    /**
     * Drills into an embedded child, whose screens hang below the form the user is on:
     * `.../test-entity/1/details` + `embedded-component/embedded_1_1/details`.
     *
     * Deliberately not routed through {@link EntityRouteRegistry} the way {@link navigateToRelated} is — an
     * embedded child has no absolute path, because the same child type appears under every owner that
     * carries it. Appending to the current URL is what preserves the position that identifies the row.
     */
    /**
     * The form of the entity an embedded child hangs under: its own `<entity>/<id>/details` tail removed.
     * Derived from the URL rather than remembered, so it still holds after a reload — where nothing was
     * remembered — and `navigateBack` does not fall through to the child's own list.
     */
    function determineOwnerUrl(): string {
      const currentUrl = determineCurrentUrl();
      return levelUpUrl(levelUpUrl(levelUpUrl(currentUrl)));
    }

    async function navigateToEmbedded(embeddedTypeName: string, id: string, payload?: NavigationPayload) {
      patchState(store, { entityName: embeddedTypeName });
      pushPayload(payload);
      const currentUrl = determineCurrentUrl();
      const embeddedFormPath = `${currentUrl}/${snakeCaseName(embeddedTypeName)}/${id}/${BaseUrlSegments.DetailsForm}`;
      await navigateToUrl(embeddedFormPath, currentUrl);
    }

    async function navigateToRelatedList(relatedTypeName: string, returnTo?: string, payload?: NavigationPayload) {
      patchState(store, { entityName: relatedTypeName });
      pushPayload(payload);
      const registeredPath = entityRouteRegistry.listPath(relatedTypeName);
      if (registeredPath) {
        await navigateToUrl(registeredPath, returnTo);
        return;
      }
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
      await navigate(url);
    }

    /** The navigation itself, without the `returnTo` bookkeeping the callers differ on. */
    async function navigate(url: string): Promise<void> {
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
      determineOwnerUrl,
      determineActiveRouteSegment,
      navigateBack,
      navigateToBreadcrumbLevel,
      navigateToDetails,
      navigateToEmbedded,
      navigateToList,
      navigateToRelated,
      navigateToRelatedList,
      navigateToTab,
      navigateToUrl,
      registerTabSegments,
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
        activeTabSegment: navigatorStore.activeTabSegment,
        breadcrumb: navigatorStore.breadcrumb,
        entityName: navigatorStore.entityName,
        navigationError: navigatorStore.navigationError,
        navigateTo: navigatorStore.navigateTo,
        navigatorPayloads: navigatorStore.requestPayloads,
        requestPayloads: navigatorStore.requestPayloads,
        responsePayloads: navigatorStore.responsePayloads,
        returnTo: navigatorStore.returnTo,
        determineActiveRouteSegment: navigatorStore.determineActiveRouteSegment,
        determineCurrentUrl: navigatorStore.determineCurrentUrl,
        determineOwnerUrl: navigatorStore.determineOwnerUrl,
        navigateBack: navigatorStore.navigateBack,
        navigateToBreadcrumbLevel: navigatorStore.navigateToBreadcrumbLevel,
        navigateToDetails: (id: string, returnTo?: string, payload?: NavigationPayload) => navigatorStore.navigateToDetails(entityName, id, returnTo, payload),
        navigateToEmbedded: navigatorStore.navigateToEmbedded,
        navigateToList: (returnTo?: string, payload?: NavigationPayload) => navigatorStore.navigateToList(entityName, returnTo, payload),
        navigateToRelated: navigatorStore.navigateToRelated,
        navigateToRelatedList: navigatorStore.navigateToRelatedList,
        navigateToTab: (id: string, segment: string, returnTo?: string, payload?: NavigationPayload) => navigatorStore.navigateToTab(entityName, id, segment, returnTo, payload),
        navigateToUrl: navigatorStore.navigateToUrl,
        registerTabSegments: navigatorStore.registerTabSegments,
        popRequestPayload: navigatorStore.popRequestPayload,
        popResponsePayload: navigatorStore.popResponsePayload,
        pushResponsePayload: navigatorStore.pushResponsePayload,
      };
    }),
  );
}
