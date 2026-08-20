import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Route } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityDescriptor, BaseEntityScreensComponent, ENTITY_DESCRIPTOR_ROUTE_DATA_KEY, EntityScreenResolver, EntityScreens, REQUESTED_ENTITY_ROUTE_DATA_KEY, snakeCaseName } from '@processpuzzle/base-entity';
import { DYNAMIC_ENTITY_NAME, DYNAMIC_ENTITY_PATH, dynamicEntityScreenRoutes } from './dynamic-entity.routes';

describe('dynamicEntityScreenRoutes', () => {
  const descriptor = { name: DYNAMIC_ENTITY_NAME } as unknown as BaseEntityDescriptor;
  const screens: EntityScreens = { descriptor, embeddedChildren: [] };
  const resolve = vi.fn<(entityName: string | undefined) => Promise<EntityScreens | undefined>>();

  beforeEach(() => {
    resolve.mockReset();
    TestBed.configureTestingModule({ providers: [{ provide: EntityScreenResolver, useValue: { resolve } }] });
  });

  const routes = (): Promise<Route[]> => TestBed.runInInjectionContext(() => dynamicEntityScreenRoutes());

  it('mounts the entity at the path it is already at, so the segment is not doubled', () => {
    // What makes the URLs come out as `dynamic-entity/list` rather than `dynamic-entity/dynamic-entity/list`.
    expect(DYNAMIC_ENTITY_PATH).toBe(snakeCaseName(DYNAMIC_ENTITY_NAME));
  });

  it('asks the resolver for the entity by name', async () => {
    resolve.mockResolvedValue(screens);

    await routes();

    expect(resolve).toHaveBeenCalledWith(DYNAMIC_ENTITY_NAME);
  });

  it('returns a single path-less route carrying the resolved descriptor', async () => {
    resolve.mockResolvedValue(screens);

    const result = await routes();

    expect(result).toHaveLength(1);
    expect(result[0].path).toBe('');
    expect(result[0].component).toBe(BaseEntityScreensComponent);
    expect(result[0].data?.[ENTITY_DESCRIPTOR_ROUTE_DATA_KEY]).toBe(descriptor);
    expect(result[0].data?.[REQUESTED_ENTITY_ROUTE_DATA_KEY]).toBe(DYNAMIC_ENTITY_NAME);
  });

  it('collapses the generated screens into one path-less group', async () => {
    resolve.mockResolvedValue(screens);

    const result = await routes();

    // A group with a path would put the entity's segment in the URL twice; `hostPath` is what prevents it.
    expect(result[0].children).toHaveLength(1);
    expect(result[0].children?.[0].path).toBe('');
  });

  it('yields a childless leaf when the backend knows no such entity', async () => {
    resolve.mockResolvedValue(undefined);

    const result = await routes();

    // Not an error: the route still renders, with `BaseEntityScreensComponent`'s "not registered" notice.
    expect(result[0].component).toBe(BaseEntityScreensComponent);
    expect(result[0].data?.[ENTITY_DESCRIPTOR_ROUTE_DATA_KEY]).toBeUndefined();
    expect(result[0].children).toBeUndefined();
  });
});
