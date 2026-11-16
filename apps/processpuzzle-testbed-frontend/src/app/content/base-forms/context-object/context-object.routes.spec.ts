import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Route } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityDescriptor, ContextCollectionComponent, EntityScreenResolver, EntityScreens, snakeCaseName } from '@processpuzzle/base-entity';
import {
  AGGREGATE_ROOT_PATH,
  COMPONENT_ONE_PATH,
  COMPONENT_TWO_PATH,
  contextObjectRoutes,
  DEMO_COMPONENT_ONE_NAME,
  DEMO_COMPONENT_TWO_NAME,
  DEMO_CONTEXT_NAME,
  DEMO_ROOT_NAME,
} from './context-object.routes';

describe('contextObjectRoutes', () => {
  class ComponentOneFacade {}
  class ComponentTwoFacade {}
  const descriptor = { name: DEMO_ROOT_NAME } as unknown as BaseEntityDescriptor;
  const screens: EntityScreens = {
    descriptor,
    embeddedChildren: [
      { entityName: DEMO_COMPONENT_ONE_NAME, facade: ComponentOneFacade as never },
      { entityName: DEMO_COMPONENT_TWO_NAME, facade: ComponentTwoFacade as never },
    ],
    extraTabs: [],
  };
  const resolve = vi.fn<(entityName: string | undefined) => Promise<EntityScreens | undefined>>();

  beforeEach(() => {
    resolve.mockReset();
    resolve.mockResolvedValue(screens);
    TestBed.configureTestingModule({ providers: [{ provide: EntityScreenResolver, useValue: { resolve } }] });
  });

  const routes = (): Promise<Route[]> => TestBed.runInInjectionContext(() => contextObjectRoutes());
  const pathsOf = (list: Route[] | undefined) => (list ?? []).map((route) => route.path);

  it("names a part's list like the part's embedded form, so the page's button stays selected while a row is edited", () => {
    expect(COMPONENT_ONE_PATH).toBe(snakeCaseName(DEMO_COMPONENT_ONE_NAME));
    expect(COMPONENT_TWO_PATH).toBe(snakeCaseName(DEMO_COMPONENT_TWO_NAME));
  });

  it('asks for the root by name, which is what gives the context its embedded parts', async () => {
    await routes();

    expect(resolve).toHaveBeenCalledWith(DEMO_ROOT_NAME);
  });

  it("serves the aggregate root's own list, with no redirect: the Samples page's buttons are these segments", async () => {
    const result = await routes();

    expect(result[0].path).toBe(AGGREGATE_ROOT_PATH);
    expect(result.some((route) => route.redirectTo !== undefined)).toBe(false);
  });

  it('serves the context below ctx/<name>, with the root as its entity and each part as a list and as an embedded branch', async () => {
    const result = await routes();

    const withRoot = result.find((route) => route.path === `ctx/${DEMO_CONTEXT_NAME}/:contextId`);
    expect(withRoot?.data).toMatchObject({ workingContext: DEMO_CONTEXT_NAME, entityName: DEMO_ROOT_NAME });
    // The lists first and the embedded branches after: a list is the whole of its path, a branch continues it.
    expect(pathsOf(withRoot?.children)).toEqual(['', COMPONENT_ONE_PATH, COMPONENT_TWO_PATH, COMPONENT_ONE_PATH, COMPONENT_TWO_PATH]);
    expect(withRoot?.children?.filter((route) => route.component === ContextCollectionComponent)).toHaveLength(2);
  });

  it("answers a part's list without a selected root with the page that asks for one", async () => {
    const result = await routes();

    expect(result.some((route) => route.path === `ctx/${DEMO_CONTEXT_NAME}`)).toBe(true);
  });

  it("gives each part's list the collection of the root it shows", async () => {
    const result = await routes();

    const lists = result.find((route) => route.path === `ctx/${DEMO_CONTEXT_NAME}/:contextId`)?.children?.filter((route) => route.component === ContextCollectionComponent);
    expect(lists?.map((route) => route.data)).toEqual([
      { collectionContext: DEMO_CONTEXT_NAME, collectionPath: 'componentsOne', collectionEntity: DEMO_COMPONENT_ONE_NAME },
      { collectionContext: DEMO_CONTEXT_NAME, collectionPath: 'componentsTwo', collectionEntity: DEMO_COMPONENT_TWO_NAME },
    ]);
  });

  it('still serves the lists when the backend knows no such entity, with no embedded branches', async () => {
    resolve.mockResolvedValue(undefined);

    const result = await routes();

    const withRoot = result.find((route) => route.path === `ctx/${DEMO_CONTEXT_NAME}/:contextId`);
    expect(pathsOf(withRoot?.children)).toEqual(['', COMPONENT_ONE_PATH, COMPONENT_TWO_PATH]);
  });
});
