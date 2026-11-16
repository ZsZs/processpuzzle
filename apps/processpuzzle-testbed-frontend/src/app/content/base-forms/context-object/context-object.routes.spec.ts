import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Route } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityDescriptor, EntityScreenResolver, EntityScreens, snakeCaseName } from '@processpuzzle/base-entity';
import { COMPONENT_ONE_PATH, COMPONENT_TWO_PATH, contextObjectRoutes, DEMO_COMPONENT_ONE_NAME, DEMO_COMPONENT_TWO_NAME, DEMO_ROOT_NAME, DEMO_ROOT_PATH } from './context-object.routes';

describe('contextObjectRoutes', () => {
  const screens: EntityScreens = { descriptor: { name: DEMO_ROOT_NAME } as unknown as BaseEntityDescriptor, embeddedChildren: [], extraTabs: [] };
  const resolve = vi.fn<(entityName: string | undefined) => Promise<EntityScreens | undefined>>();

  beforeEach(() => {
    resolve.mockReset();
    resolve.mockResolvedValue(screens);
    TestBed.configureTestingModule({ providers: [{ provide: EntityScreenResolver, useValue: { resolve } }] });
  });

  const routes = (): Promise<Route[]> => TestBed.runInInjectionContext(() => contextObjectRoutes());

  it('names the segments like the entities, which is what the URLs and the context name are made of', () => {
    expect(DEMO_ROOT_PATH).toBe(snakeCaseName(DEMO_ROOT_NAME));
    expect(COMPONENT_ONE_PATH).toBe(snakeCaseName(DEMO_COMPONENT_ONE_NAME));
    expect(COMPONENT_TWO_PATH).toBe(snakeCaseName(DEMO_COMPONENT_TWO_NAME));
  });

  it("contributes the root's ordinary screens and nothing about a context", async () => {
    const result = await routes();

    expect(resolve).toHaveBeenCalledWith(DEMO_ROOT_NAME);
    expect(result).toHaveLength(1);
    expect(result[0].path).toBe(DEMO_ROOT_PATH);
    expect(JSON.stringify(result[0].data ?? {})).not.toContain('workingContext');
  });
});
