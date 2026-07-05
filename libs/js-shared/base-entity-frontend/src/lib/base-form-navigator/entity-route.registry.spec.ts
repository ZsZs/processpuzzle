import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, RouteConfigLoadEnd, Routes } from '@angular/router';
import { Component } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { EntityRouteRegistry, ENTITY_NAME_ROUTE_DATA_KEY } from './entity-route.registry';

@Component({ standalone: true, template: '' })
class DummyComponent {}

function createRegistry(routes: Routes): { registry: EntityRouteRegistry; router: Router } {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideRouter(routes)],
  });
  const registry = TestBed.inject(EntityRouteRegistry);
  const router = TestBed.inject(Router);
  return { registry, router };
}

describe('EntityRouteRegistry', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('collects base paths for entities declared on top-level routes', () => {
    const { registry } = createRegistry([
      { path: 'orders', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: 'Order' } },
      { path: 'invoices', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: 'Invoice' } },
      { path: 'ignored', component: DummyComponent },
    ]);

    registry.scan();

    expect(registry.basePath('Order')).toBe('/orders');
    expect(registry.basePath('Invoice')).toBe('/invoices');
    expect(registry.basePath('Missing')).toBeUndefined();
    expect([...registry.registeredEntities()].sort()).toEqual(['Invoice', 'Order']);
  });

  it('follows child routes and preserves the parent segment in the base path', () => {
    const { registry } = createRegistry([
      {
        path: 'shop',
        component: DummyComponent,
        children: [{ path: 'products', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: 'Product' } }],
      },
    ]);

    registry.scan();

    expect(registry.basePath('Product')).toBe('/shop/products');
  });

  it('follows already-loaded lazy routes via _loadedRoutes', () => {
    const lazyRoute = {
      path: 'admin',
      component: DummyComponent,
      _loadedRoutes: [{ path: 'users', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: 'User' } }],
    };

    const { registry } = createRegistry([lazyRoute]);

    registry.scan();

    expect(registry.basePath('User')).toBe('/admin/users');
  });

  it('treats routes without a path segment as pass-through when appending', () => {
    const { registry } = createRegistry([
      {
        matcher: () => null,
        component: DummyComponent,
        children: [{ path: 'reports', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: 'Report' } }],
      },
    ]);

    registry.scan();

    expect(registry.basePath('Report')).toBe('/reports');
  });

  it('ignores routes whose entityName is empty or not a string', () => {
    const { registry } = createRegistry([
      { path: 'a', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: '' } },
      { path: 'b', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: 42 as unknown as string } },
      { path: 'c', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: 'Kept' } },
    ]);

    registry.scan();

    expect(registry.registeredEntities()).toEqual(['Kept']);
  });

  it('exposes derived list and details paths per entity', () => {
    const { registry } = createRegistry([{ path: 'orders', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: 'Order' } }]);

    registry.scan();

    expect(registry.listPath('Order')).toBe('/orders/list');
    expect(registry.detailsPath('Order', '42')).toBe('/orders/42/details');
    expect(registry.listPath('Missing')).toBeUndefined();
    expect(registry.detailsPath('Missing', '1')).toBeUndefined();
  });

  it('clears previously scanned entries so a rescan only reflects the current config', () => {
    const { registry, router } = createRegistry([{ path: 'orders', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: 'Order' } }]);
    registry.scan();
    expect(registry.registeredEntities()).toEqual(['Order']);

    router.resetConfig([{ path: 'invoices', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: 'Invoice' } }]);
    registry.scan();

    expect(registry.registeredEntities()).toEqual(['Invoice']);
    expect(registry.basePath('Order')).toBeUndefined();
  });

  it('rescans when the router emits RouteConfigLoadEnd after observeLazyLoads', () => {
    const { registry, router } = createRegistry([]);
    registry.observeLazyLoads();

    router.resetConfig([{ path: 'products', component: DummyComponent, data: { [ENTITY_NAME_ROUTE_DATA_KEY]: 'Product' } }]);
    (router.events as unknown as { next: (e: unknown) => void }).next(new RouteConfigLoadEnd({} as never));

    expect(registry.basePath('Product')).toBe('/products');
  });
});
