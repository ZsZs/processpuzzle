import { ActivatedRoute, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { signalStore } from '@ngrx/signals';
import { BaseFormNavigatorStore, RouteSegments } from './base-form-navigator.store';
import { Component } from '@angular/core';
import { TestEntityComponent } from '../test-entity-component';

describe('BaseFormNavigatorStore', () => {
  @Component({
    selector: 'dummy-component',
    template: ` <div></div>`,
    standalone: true,
  })
  class DummyComponent {}

  const NavigatorStore = signalStore({ providedIn: 'root' }, BaseFormNavigatorStore('TestEntity'));
  let route: ActivatedRoute;
  let store: any;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [],
      providers: [
        provideLocationMocks(),
        provideRouter([
          { path: 'home', component: DummyComponent },
          { path: 'test-entity/:id/details', component: DummyComponent },
          { path: 'test-entity/list', component: DummyComponent },
          { path: 'test-entity-component/:id/details', component: DummyComponent },
          { path: 'test-entity-component/list', component: DummyComponent },
        ]),
        NavigatorStore,
      ],
    }).compileComponents();
    await RouterTestingHarness.create('home');
    store = TestBed.inject(NavigatorStore);
    route = TestBed.inject(ActivatedRoute);
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
    expect(Reflect.get(route, '_routerState').snapshot.url).toEqual('/home');
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

  it('navigateToUrl() navigates to url from store.', async () => {
    await store.navigateToUrl('test-entity/list', 'home');
    expect(store.navigateTo()).toEqual('test-entity/list');
    expect(store.returnTo()).toEqual('home');
    expect(store.activeRouteSegment()).toEqual(RouteSegments.LIST_ROUTE);
  });
});
