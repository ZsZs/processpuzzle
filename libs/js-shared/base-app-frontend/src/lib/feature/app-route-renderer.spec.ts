import { TestBed } from '@angular/core/testing';
import {
  BaseEntityAttrDescriptor,
  BaseEntityDescriptor,
  BaseEntityScreensComponent,
  ENTITY_DESCRIPTOR_ROUTE_DATA_KEY,
  EntityScreenResolver,
  EntityScreens,
  FormControlType,
  REQUESTED_ENTITY_ROUTE_DATA_KEY,
} from '@processpuzzle/base-entity';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RouteDefinition } from '../domain/app-definition';
import { AppRouteRenderer } from './app-route-renderer';
import { RouteUnsupportedComponent } from './route-unsupported.component';
import { RouteWidgetsComponent } from './route-widgets.component';

function orderScreens(): EntityScreens {
  return {
    descriptor: new BaseEntityDescriptor({
      entityName: 'Order',
      entityTitle: 'Order',
      attrDescriptors: [new BaseEntityAttrDescriptor('orderNumber', FormControlType.TEXT_BOX, 'Order #', undefined, true)],
    }),
    embeddedChildren: [],
  };
}

/**
 * The URL shape of an entity's screens belongs to base-entity and is covered by `entityScreenRoute`'s own
 * spec. What is asserted here is only what this renderer decides: which kind maps to what, and which parts of
 * a `RouteDefinition` reach the route.
 */
describe('AppRouteRenderer', () => {
  const entityScreens = { resolve: vi.fn<(entityName?: string) => Promise<EntityScreens | undefined>>() };

  function renderer(): AppRouteRenderer {
    TestBed.configureTestingModule({ providers: [{ provide: EntityScreenResolver, useValue: entityScreens }] });
    return TestBed.inject(AppRouteRenderer);
  }

  function entityDefinition(path: string, entityName = 'Order'): RouteDefinition {
    return new RouteDefinition({ path, title: path, kind: 'ENTITY', entityName });
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.resetAllMocks();
    entityScreens.resolve.mockResolvedValue(orderScreens());
  });

  it('renders a WIDGETS route with its widgets on the route data', async () => {
    const route = await renderer().render(new RouteDefinition({ path: 'home', title: 'Welcome', kind: 'WIDGETS', widgets: [] }));

    expect(route.component).toBe(RouteWidgetsComponent);
    expect(route.data?.['widgets']).toEqual([]);
  });

  it('names the reason on a DOCUMENT route rather than rendering nothing', async () => {
    const route = await renderer().render(new RouteDefinition({ path: 'handbook', title: 'Handbook', kind: 'DOCUMENT' }));

    expect(route.component).toBe(RouteUnsupportedComponent);
    expect(route.data?.['reason']).toContain('handbook');
  });

  it('names the reason on a route kind it does not recognize', async () => {
    const route = await renderer().render({ path: 'x', title: 'X', kind: 'CHART' } as unknown as RouteDefinition);

    expect(route.component).toBe(RouteUnsupportedComponent);
    expect(route.data?.['reason']).toContain('CHART');
  });

  describe('an ENTITY route', () => {
    it('resolves the entity the definition names and mounts base-entity screens for it', async () => {
      const screens = orderScreens();
      entityScreens.resolve.mockResolvedValue(screens);

      const route = await renderer().render(entityDefinition('order-list'));

      expect(entityScreens.resolve).toHaveBeenCalledWith('Order');
      expect(route.component).toBe(BaseEntityScreensComponent);
      expect(route.data?.[ENTITY_DESCRIPTOR_ROUTE_DATA_KEY]).toBe(screens.descriptor);
      expect(route.data?.[REQUESTED_ENTITY_ROUTE_DATA_KEY]).toBe('Order');
      expect(route.children).toBeDefined();
    });

    it('keeps the authored title', async () => {
      const route = await renderer().render(entityDefinition('order-list'));

      expect(route.title).toBeDefined();
    });

    // Read and carried, applied by nothing yet — see the note on the renderer.
    it('carries the authored entityMode and rsqlFilter through', async () => {
      const definition = new RouteDefinition({ path: 'orders', title: 'Orders', kind: 'ENTITY', entityName: 'Order', entityMode: 'DETAILS', rsqlFilter: 'status==DRAFT' });

      const route = await renderer().render(definition);

      expect(route.data?.['entityMode']).toBe('DETAILS');
      expect(route.data?.['rsqlFilter']).toBe('status==DRAFT');
    });

    it('passes the authored path along, so the screens are not mounted under a duplicate segment', async () => {
      const route = await renderer().render(entityDefinition('order'));

      // A path that already is the entity's snake-case name gets a path-less group. See entityScreenRoute.
      expect(route.children?.map((child) => child.path)).toEqual(['']);
    });

    /**
     * The nav item that links here has to render something, and an `AppDefinition` naming an entity that was
     * renamed — or whose definitions this deployment does not serve — is a warning rather than a failure.
     */
    it('stays a leaf when nothing answers to the entity, so the component can say so', async () => {
      entityScreens.resolve.mockResolvedValue(undefined);

      const route = await renderer().render(entityDefinition('order-list', 'Gone'));

      expect(route.component).toBe(BaseEntityScreensComponent);
      expect(route.children).toBeUndefined();
      expect(route.data?.[REQUESTED_ENTITY_ROUTE_DATA_KEY]).toBe('Gone');
    });
  });
});
