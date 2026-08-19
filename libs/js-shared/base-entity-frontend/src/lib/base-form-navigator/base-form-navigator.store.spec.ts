import { provideRouter, Router, RouterOutlet } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { signalStore } from '@ngrx/signals';
import { BaseFormNavigatorStore, RouteSegments } from './base-form-navigator.store';
import { Component } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { NavigatorCommand, type NavigationPayload } from './navigation-payload';

/** A route host that can carry nested screens, as an entity's details route carries an embedded child's. */
@Component({
  selector: 'outlet-component',
  template: ` <router-outlet /> `,
  standalone: true,
  imports: [RouterOutlet],
})
class OutletComponent {}

describe('BaseFormNavigatorStore', () => {
  @Component({
    selector: 'dummy-component',
    template: ` <div></div>`,
    standalone: true,
  })
  class DummyComponent {}

  const NavigatorStore = signalStore({ providedIn: 'root' }, BaseFormNavigatorStore('TestEntity'));
  const OtherNavigatorStore = signalStore({ providedIn: 'root' }, BaseFormNavigatorStore('ApplicationProperty'));
  let router: Router;
  let store: InstanceType<typeof NavigatorStore>;
  let otherStore: InstanceType<typeof OtherNavigatorStore>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [],
      providers: [
        provideLocationMocks(),
        provideRouter([
          { path: 'home', component: DummyComponent },
          { path: 'test-entity/:id/details', component: DummyComponent },
          { path: 'test-entity/:id/content', component: DummyComponent },
          // A *container* tab: its component hosts an outlet, so screens of its own answer below it. The
          // path deliberately ends in the letters of the list segment — that is what base-app's Preview tab
          // looks like with an application route named `order-list` under it.
          { path: 'test-entity/:id/content/order-list', component: DummyComponent },
          { path: 'test-entity/list', component: DummyComponent },
          { path: 'application-property/:id/details', component: DummyComponent },
          { path: 'application-property/list', component: DummyComponent },
          { path: 'test-entity-component/:id/details', component: DummyComponent },
          { path: 'test-entity-component/list', component: DummyComponent },
          // An embedded child's screens hang below the owner's details route, which is what carries its position.
          { path: 'test-entity/:id/details/embedded-component/:embeddedId/details', component: DummyComponent },
        ]),
        NavigatorStore,
        OtherNavigatorStore,
      ],
    }).compileComponents();
    await RouterTestingHarness.create('home');
    store = TestBed.inject(NavigatorStore);
    otherStore = TestBed.inject(OtherNavigatorStore);
    router = TestBed.inject(Router);
  });

  it('should be created', () => {
    expect(store).toBeTruthy();
  });

  it('determineActiveRouteSegment() analysis if route is for List or Details.', async () => {
    expect(store.activeRouteSegment()).toBeUndefined();
    await store.navigateToUrl('test-entity/list', 'home');
    expect(store.activeRouteSegment()).toEqual(RouteSegments.LIST_ROUTE);
  });

  it('navigateBack(), navigates to store.returnTo() url.', async () => {
    await store.navigateToUrl('test-entity/list', 'home');
    await store.navigateBack();
    expect(router.url).toEqual('/home');
  });

  it('navigateToDetails() navigates from current route to Details route.', async () => {
    await store.navigateToDetails('1', 'home');
    expect(store.determineCurrentUrl()).toEqual('/test-entity/1/details');
    expect(store.navigateTo()).toEqual('/test-entity/1/details');
    expect(store.returnTo()).toEqual('home');
    expect(store.activeRouteSegment()).toEqual(RouteSegments.DETAILS_ROUTE);
  });

  /**
   * An extra tab is a screen of the entity beside its details form — see `EntityTabDescriptor`. Which URL
   * tails count as one is registered by whoever renders the tabs, because the store has no descriptors: an
   * unregistered tail is not a tab and must keep classifying as it always did.
   */
  describe('extra tab routes', () => {
    it('classifies a registered tab segment, and nothing else', async () => {
      await store.navigateToUrl('test-entity/1/content', 'home');
      expect(store.activeRouteSegment()).toBeUndefined();
      expect(store.activeTabSegment()).toBeUndefined();

      store.registerTabSegments(['content']);

      expect(store.activeRouteSegment()).toEqual(RouteSegments.ENTITY_TAB_ROUTE);
      expect(store.activeTabSegment()).toEqual('content');
    });

    it('navigateToTab() builds <entity>/<id>/<segment> on the details route prefix', async () => {
      store.registerTabSegments(['content']);

      await store.navigateToTab('1', 'content', 'home');

      expect(store.determineCurrentUrl()).toEqual('/test-entity/1/content');
      expect(store.returnTo()).toEqual('home');
      expect(store.activeRouteSegment()).toEqual(RouteSegments.ENTITY_TAB_ROUTE);
    });

    /**
     * The prefix every other URL of this entity is built on. A tab URL has the details route's shape, so it
     * counts back the same three levels; read as the two-level list shape it would leave a segment of the
     * entity's own path in the prefix, and the List tab would navigate to `/test-entity/1/test-entity/list`.
     */
    it('leaves the list reachable from a tab', async () => {
      store.registerTabSegments(['content']);
      await store.navigateToTab('1', 'content');

      await store.navigateToList();

      expect(store.determineCurrentUrl()).toEqual('/test-entity/list');
    });

    it('is inert when the tab it would navigate to is already open', async () => {
      store.registerTabSegments(['content']);
      await store.navigateToTab('1', 'content', 'home');

      await store.navigateToTab('1', 'content', 'elsewhere');

      expect(store.returnTo()).toEqual('home');
    });

    /**
     * A container tab keeps its own screens below it, so the URL continues past the tab segment. Read only
     * at the end of the URL the tab went unrecognized — and since `order-list` ends in the letters of the
     * list segment, it classified as the *list form*, which is what put an entity toolbar and an active List
     * link on a previewed application.
     */
    it('stays on a container tab for a URL below it', async () => {
      store.registerTabSegments(['content']);

      await store.navigateToUrl('test-entity/1/content/order-list', 'home');

      expect(store.activeRouteSegment()).toEqual(RouteSegments.ENTITY_TAB_ROUTE);
      expect(store.activeTabSegment()).toEqual('content');
    });

    it('classifies no form at all below an unregistered tab whose path ends in a form segment’s letters', async () => {
      await store.navigateToUrl('test-entity/1/content/order-list', 'home');

      expect(store.activeRouteSegment()).toBeUndefined();
    });

    /** The prefix is located at the tab, not counted back from the end, so a container tab's depth is irrelevant. */
    it('leaves the list reachable from below a container tab', async () => {
      store.registerTabSegments(['content']);
      await store.navigateToUrl('test-entity/1/content/order-list', 'home');

      await store.navigateToList();

      expect(store.determineCurrentUrl()).toEqual('/test-entity/list');
    });

    it('clears the tab segment again on a details or list route', async () => {
      store.registerTabSegments(['content']);
      await store.navigateToTab('1', 'content');
      expect(store.activeTabSegment()).toEqual('content');

      await store.navigateToDetails('1');

      expect(store.activeRouteSegment()).toEqual(RouteSegments.DETAILS_ROUTE);
      expect(store.activeTabSegment()).toBeUndefined();
    });
  });

  it('navigateToList() navigates from current route to List route', async () => {
    await store.navigateToList('home');
    expect(store.determineCurrentUrl()).toEqual('/test-entity/list');
    expect(store.navigateTo()).toEqual('/test-entity/list');
    expect(store.returnTo()).toEqual('home');
    expect(store.activeRouteSegment()).toEqual(RouteSegments.LIST_ROUTE);
  });

  it('navigateToRelated() navigates to related entities Details route.', async () => {
    await store.navigateToRelated('TestEntityComponent', '2', 'home');
    expect(store.determineCurrentUrl()).toEqual('/test-entity-component/2/details');
    expect(store.navigateTo()).toEqual('/test-entity-component/2/details');
    expect(store.returnTo()).toEqual('home');
    expect(store.activeRouteSegment()).toEqual(RouteSegments.DETAILS_ROUTE);
  });

  it('navigateToEmbedded() appends the child to the current URL rather than resolving an absolute path.', async () => {
    await store.navigateToUrl('test-entity/1/details', 'home');

    await store.navigateToEmbedded('EmbeddedComponent', 'embedded_1_1');

    expect(store.determineCurrentUrl()).toEqual('/test-entity/1/details/embedded-component/embedded_1_1/details');
    // Back goes to the owner's form, which is where the drill-down started.
    expect(store.returnTo()).toEqual('/test-entity/1/details');
  });

  it('navigateToEmbedded() returns to the owner form on navigateBack().', async () => {
    await store.navigateToUrl('test-entity/1/details', 'home');
    await store.navigateToEmbedded('EmbeddedComponent', 'embedded_1_1');

    await store.navigateBack();

    expect(store.determineCurrentUrl()).toEqual('/test-entity/1/details');
  });

  it('navigateToRelatedList() navigates to related entities List route.', async () => {
    await store.navigateToRelatedList('TestEntityComponent', 'home');
    expect(store.determineCurrentUrl()).toEqual('/test-entity-component/list');
    expect(store.navigateTo()).toEqual('/test-entity-component/list');
    expect(store.returnTo()).toEqual('home');
    expect(store.activeRouteSegment()).toEqual(RouteSegments.LIST_ROUTE);
  });

  it('navigation methods add given payloads to the navigator payload map, keyed by attrName.', async () => {
    const detailsPayload: NavigationPayload = { command: NavigatorCommand.EDIT, attrName: 'editTarget', payload: { id: '1' } };
    const listPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'component', payload: { entityName: 'TestEntity' } };

    await store.navigateToDetails('1', 'home', detailsPayload);
    await store.navigateToRelatedList('TestEntityComponent', 'home', listPayload);

    expect(Array.from(store.navigatorPayloads().values())).toEqual([detailsPayload, listPayload]);
    expect(store.navigatorPayloads().get('editTarget')).toEqual(detailsPayload);
    expect(store.navigatorPayloads().get('component')).toEqual(listPayload);
  });

  it('navigateBack() removes the latest navigator payload from the map.', async () => {
    const detailsPayload: NavigationPayload = { command: NavigatorCommand.EDIT, attrName: 'editTarget', payload: { id: '1' } };
    const relatedPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'component', payload: { entityName: 'TestEntityComponent' } };

    await store.navigateToDetails('1', 'home', detailsPayload);
    await store.navigateToRelated('TestEntityComponent', '2', 'home', relatedPayload);
    await store.navigateBack();

    expect(Array.from(store.navigatorPayloads().values())).toEqual([detailsPayload]);
  });

  it('direct router navigation clears request and response payload maps.', async () => {
    const detailsPayload: NavigationPayload = { command: NavigatorCommand.EDIT, attrName: 'editTarget', payload: { id: '1' } };
    const responsePayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'component', payload: { id: '2' } };

    await store.navigateToDetails('1', 'home', detailsPayload);
    store.pushResponsePayload(responsePayload);
    await router.navigateByUrl('/home');

    expect(store.requestPayloads().size).toEqual(0);
    expect(store.responsePayloads().size).toEqual(0);
  });

  it('popResponsePayload(attrName) removes and returns the response payload for that attribute.', () => {
    const editPayload: NavigationPayload = { command: NavigatorCommand.EDIT, attrName: 'editTarget', payload: { id: '1' } };
    const customerPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'customerId', payload: { id: '2' } };
    const shipperPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'shipperId', payload: { id: '3' } };

    store.pushResponsePayload(editPayload);
    store.pushResponsePayload(customerPayload);
    store.pushResponsePayload(shipperPayload);

    expect(store.popResponsePayload('customerId')).toEqual(customerPayload);
    expect(Array.from(store.responsePayloads().values())).toEqual([editPayload, shipperPayload]);
  });

  it('popResponsePayload() without an attrName returns and removes the latest response payload.', () => {
    const customerPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'customerId', payload: { id: '2' } };
    const shipperPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'shipperId', payload: { id: '3' } };

    store.pushResponsePayload(customerPayload);
    store.pushResponsePayload(shipperPayload);

    expect(store.popResponsePayload()).toEqual(shipperPayload);
    expect(Array.from(store.responsePayloads().values())).toEqual([customerPayload]);
  });

  it('pushing a payload with the same attrName replaces the prior entry and moves it to the end.', () => {
    const firstPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'customerId', payload: { id: '1' } };
    const secondPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'shipperId', payload: { id: '2' } };
    const replacementPayload: NavigationPayload = { command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'customerId', payload: { id: '3' } };

    store.pushResponsePayload(firstPayload);
    store.pushResponsePayload(secondPayload);
    store.pushResponsePayload(replacementPayload);

    expect(Array.from(store.responsePayloads().values())).toEqual([secondPayload, replacementPayload]);
  });

  it('navigateToUrl() navigates to url from store.', async () => {
    await store.navigateToUrl('test-entity/list', 'home');
    expect(store.navigateTo()).toEqual('test-entity/list');
    expect(store.returnTo()).toEqual('home');
    expect(store.activeRouteSegment()).toEqual(RouteSegments.LIST_ROUTE);
  });

  it('shares navigator state with other stores using the same feature.', async () => {
    await store.navigateToUrl('test-entity/list', 'home');

    expect(otherStore.navigateTo()).toEqual('test-entity/list');
    expect(otherStore.returnTo()).toEqual('home');
    expect(otherStore.activeRouteSegment()).toEqual(RouteSegments.LIST_ROUTE);
  });

  it('uses each store entity name while delegating to the singleton navigator.', async () => {
    await otherStore.navigateToDetails('1', 'home');

    expect(otherStore.determineCurrentUrl()).toEqual('/application-property/1/details');
    expect(store.determineCurrentUrl()).toEqual('/application-property/1/details');
    expect(otherStore.navigateTo()).toEqual('/application-property/1/details');
    expect(store.navigateTo()).toEqual('/application-property/1/details');
    expect(otherStore.entityName()).toEqual('ApplicationProperty');
  });
});

/**
 * The breadcrumb needs routes shaped the way the framework's own are: the entity named on the branch, the row
 * on its `:entityId/details` child, and an embedded child hanging below that details route.
 */
describe('BaseFormNavigatorStore breadcrumb', () => {
  const DEEP_URL = '/samples/test-entity/1/details/embedded-component/embedded_1_1/details';
  const NavigatorStore = signalStore({ providedIn: 'root' }, BaseFormNavigatorStore('Test Entity'));
  let store: InstanceType<typeof NavigatorStore>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [
        provideLocationMocks(),
        provideRouter([
          { path: 'home', component: OutletComponent },
          {
            path: 'samples',
            children: [
              {
                path: 'test-entity',
                data: { entityName: 'Test Entity' },
                children: [
                  { path: 'list', component: OutletComponent },
                  {
                    path: ':entityId/details',
                    component: OutletComponent,
                    children: [
                      {
                        path: 'embedded-component',
                        data: { entityName: 'Embedded Component' },
                        children: [{ path: ':entityId/details', component: OutletComponent }],
                      },
                    ],
                  },
                ],
              },
            ],
          },
        ]),
        NavigatorStore,
      ],
    }).compileComponents();
    await RouterTestingHarness.create('home');
    store = TestBed.inject(NavigatorStore);
  });

  it('describes the levels the current URL walks through, outermost first.', async () => {
    await store.navigateToUrl(DEEP_URL);

    expect(store.breadcrumb().map((level) => [level.entityName, level.entityId])).toEqual([
      ['Test Entity', '1'],
      ['Embedded Component', 'embedded_1_1'],
    ]);
  });

  it('navigateToBreadcrumbLevel() walks up to that level and leaves it its own back target.', async () => {
    await store.navigateToUrl(DEEP_URL);

    await store.navigateToBreadcrumbLevel(0);

    expect(store.determineCurrentUrl()).toEqual('/samples/test-entity/1/details');
    expect(store.returnTo()).toEqual('');
  });

  /** Counting segments up from the end would land in the middle of the embedded branch. */
  it('builds a list URL from the entity’s own place in the breadcrumb, at any depth.', async () => {
    await store.navigateToUrl(DEEP_URL);

    await store.navigateToList();

    expect(store.determineCurrentUrl()).toEqual('/samples/test-entity/list');
  });
});
