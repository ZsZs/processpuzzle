import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Route } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityDescriptor, BaseEntityScreensComponent, ENTITY_DESCRIPTOR_ROUTE_DATA_KEY, EntityScreenResolver, EntityScreens, REQUESTED_ENTITY_ROUTE_DATA_KEY, snakeCaseName } from '@processpuzzle/base-entity';
import { ORDER_NAME, ORDER_PATH, orderScreenRoutes, SPECIAL_ORDER_NAME, SPECIAL_ORDER_PATH, specialOrderScreenRoutes } from './rule-sample.routes';

describe('rule sample screen routes', () => {
  const screensOf = (name: string): EntityScreens => ({ descriptor: { name } as unknown as BaseEntityDescriptor, embeddedChildren: [], extraTabs: [] });
  const resolve = vi.fn<(entityName: string | undefined) => Promise<EntityScreens | undefined>>();

  beforeEach(() => {
    resolve.mockReset();
    TestBed.configureTestingModule({ providers: [{ provide: EntityScreenResolver, useValue: { resolve } }] });
  });

  const routes = (loadChildren: () => Promise<Route[]>): Promise<Route[]> => TestBed.runInInjectionContext(loadChildren);

  it.each([
    [ORDER_NAME, ORDER_PATH],
    [SPECIAL_ORDER_NAME, SPECIAL_ORDER_PATH],
  ])('mounts %s at the segment the form navigator builds', (entityName, path) => {
    // What keeps the URLs at `order/list` rather than `order/order/list`, and what makes the Name column's
    // link and the tab links resolve at all.
    expect(path).toBe(snakeCaseName(entityName));
  });

  it.each([
    ['Order', orderScreenRoutes, ORDER_NAME],
    ['Special Order', specialOrderScreenRoutes, SPECIAL_ORDER_NAME],
  ])('asks the resolver for %s by name', async (_label, loadChildren, entityName) => {
    resolve.mockResolvedValue(screensOf(entityName));

    await routes(loadChildren);

    expect(resolve).toHaveBeenCalledWith(entityName);
  });

  it('returns a single path-less route carrying the resolved descriptor', async () => {
    const screens = screensOf(ORDER_NAME);
    resolve.mockResolvedValue(screens);

    const result = await routes(orderScreenRoutes);

    expect(result).toHaveLength(1);
    expect(result[0].path).toBe('');
    expect(result[0].component).toBe(BaseEntityScreensComponent);
    expect(result[0].data?.[ENTITY_DESCRIPTOR_ROUTE_DATA_KEY]).toBe(screens.descriptor);
    expect(result[0].data?.[REQUESTED_ENTITY_ROUTE_DATA_KEY]).toBe(ORDER_NAME);
  });

  it('collapses the generated screens into one path-less group', async () => {
    resolve.mockResolvedValue(screensOf(SPECIAL_ORDER_NAME));

    const result = await routes(specialOrderScreenRoutes);

    // A group with a path would put `special-order` in the URL twice; `hostPath` is what prevents it.
    expect(result[0].children).toHaveLength(1);
    expect(result[0].children?.[0].path).toBe('');
  });

  it('yields a childless leaf when the tenant has no such definition', async () => {
    resolve.mockResolvedValue(undefined);

    const result = await routes(orderScreenRoutes);

    // Not an error: a deployment that seeds no entity definitions still renders the tab, with
    // `BaseEntityScreensComponent`'s "not registered" notice instead of a list.
    expect(result[0].component).toBe(BaseEntityScreensComponent);
    expect(result[0].data?.[ENTITY_DESCRIPTOR_ROUTE_DATA_KEY]).toBeUndefined();
    expect(result[0].children).toBeUndefined();
  });
});
