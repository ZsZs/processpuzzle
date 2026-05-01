import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { signalStore } from '@ngrx/signals';
import { BaseFormNavigatorStore, RouteSegments } from './base-form-navigator.store';
import { Component } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';

describe('BaseFormNavigatorStore', () => {
  @Component({
    selector: 'dummy-component',
    template: ` <div></div>`,
    standalone: true,
  })
  class DummyComponent {}

  const NavigatorStore = signalStore({ providedIn: 'root' }, BaseFormNavigatorStore('TestEntity'));
  const OtherNavigatorStore = signalStore({ providedIn: 'root' }, BaseFormNavigatorStore('ApplicationProperty'));
  let route: ActivatedRoute;
  let router: Router;
  let store: any;
  let otherStore: any;

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
    route = TestBed.inject(ActivatedRoute);
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
