import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { signalStore } from '@ngrx/signals';
import { BaseFormNavigatorStore, RouteSegments } from './base-form-navigator.store';
import { Component } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { NavigatorCommand, type NavigationPayload } from './navigation-payload';

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
          { path: 'test-entity/list', component: DummyComponent },
          { path: 'application-property/:id/details', component: DummyComponent },
          { path: 'application-property/list', component: DummyComponent },
          { path: 'test-entity-component/:id/details', component: DummyComponent },
          { path: 'test-entity-component/list', component: DummyComponent },
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
