import { InjectionToken } from '@angular/core';
import { Route, Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityFacade } from '../base-entity-facade/base-entity-facade';
import { BaseEntity } from '../base-entity/base-entity';
import { ENTITY_NAME_ROUTE_DATA_KEY } from '../base-form-navigator/entity-route.registry';
import { entityScreenRoute } from './entity-screen-routes';
import { BaseEntityScreensComponent, ENTITY_DESCRIPTOR_ROUTE_DATA_KEY, REQUESTED_ENTITY_ROUTE_DATA_KEY } from './entity-screens.component';
import { EntityScreens } from './entity-screens.resolver';

function orderDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: 'Order',
    entityTitle: 'Order',
    attrDescriptors: [new BaseEntityAttrDescriptor('orderNumber', FormControlType.TEXT_BOX, 'Order #', undefined, true)],
  });
}

function screensOf(embeddedChildren: EntityScreens['embeddedChildren'] = [], extraTabs: EntityScreens['extraTabs'] = []): EntityScreens {
  return { descriptor: orderDescriptor(), embeddedChildren, extraTabs };
}

/** The route carrying the entity's own `baseEntityRoutes`, i.e. the one that declares the snake-case segment. */
function entityGroupOf(route: Route): Route | undefined {
  return route.children?.find((child) => child.children?.some((grandChild) => grandChild.path === 'list'));
}

describe('entityScreenRoute', () => {
  it('renders the entity screens component and carries the descriptor for it to read', () => {
    const screens = screensOf();

    const route = entityScreenRoute({ entityName: 'Order', screens });

    expect(route.component).toBe(BaseEntityScreensComponent);
    expect(route.data?.[ENTITY_DESCRIPTOR_ROUTE_DATA_KEY]).toBe(screens.descriptor);
    expect(route.data?.[REQUESTED_ENTITY_ROUTE_DATA_KEY]).toBe('Order');
  });

  it('leaves `path` to whoever mounts it', () => {
    expect(entityScreenRoute({ entityName: 'Order', screens: screensOf() })).not.toHaveProperty('path');
  });

  it('merges the data the host supplies with its own', () => {
    const route = entityScreenRoute({ entityName: 'Order', screens: screensOf(), data: { entityMode: 'LIST' } });

    expect(route.data?.['entityMode']).toBe('LIST');
    expect(route.data?.[REQUESTED_ENTITY_ROUTE_DATA_KEY]).toBe('Order');
  });

  /**
   * `BaseFormNavigatorSingletonStore` composes every URL as `<base>/<snakeCaseName>/…`, so the entity's own
   * segment has to be in the URL. Without it the tab bar navigates to `…/order/list` for screens mounted at
   * `…/order-list/list`, and every tab click and row link silently matches nothing.
   */
  it('mounts the screens under the snake-case entity name', () => {
    const route = entityScreenRoute({ entityName: 'Order', screens: screensOf(), hostPath: 'order-list' });

    expect(route.children?.map((child) => child.path)).toEqual(['', 'order']);
    expect(entityGroupOf(route)?.path).toBe('order');
  });

  it('lands the host path itself on the entity list', () => {
    const route = entityScreenRoute({ entityName: 'Order', screens: screensOf(), hostPath: 'order-list' });

    // pathMatch full, or this would prefix-match the entity's own children and redirect in a loop.
    expect(route.children?.[0]).toEqual({ path: '', pathMatch: 'full', redirectTo: 'order/list' });
  });

  it('mounts the list and details routes base-entity already defines', () => {
    const route = entityScreenRoute({ entityName: 'Order', screens: screensOf(), hostPath: 'order-list' });

    expect(entityGroupOf(route)?.children?.map((child) => child.path)).toEqual(['', ':entityId/details', 'list']);
  });

  /** `readEmbeddedRouteChain` reads this key to place the level an embedded drill-down hangs off. */
  it('names the entity on the route contributing the snake-case segment, and only there', () => {
    const route = entityScreenRoute({ entityName: 'Order', screens: screensOf(), hostPath: 'order-list' });

    expect(entityGroupOf(route)?.data?.[ENTITY_NAME_ROUTE_DATA_KEY]).toBe('Order');
    expect(route.data?.[ENTITY_NAME_ROUTE_DATA_KEY]).toBeUndefined();
  });

  it('mounts the group path-less when the host path already is the snake-case name, keeping the URL short', () => {
    const route = entityScreenRoute({ entityName: 'Order', screens: screensOf(), hostPath: 'order' });

    expect(route.children?.map((child) => child.path)).toEqual(['']);
    // baseEntityRoutes' own '' -> list redirect does the landing here, so no second one is added.
    expect(entityGroupOf(route)?.children?.map((child) => child.path)).toEqual(['', ':entityId/details', 'list']);
  });

  it('recognizes the snake-case name in the last segment of a multi-segment host path', () => {
    const route = entityScreenRoute({ entityName: 'Order', screens: screensOf(), hostPath: 'sales/order' });

    expect(route.children?.map((child) => child.path)).toEqual(['']);
  });

  it('nests the snake-case segment when no host path is given at all', () => {
    const route = entityScreenRoute({ entityName: 'Order', screens: screensOf() });

    expect(entityGroupOf(route)?.path).toBe('order');
  });

  it('mounts a branch per embedded child, below the entity details route', async () => {
    const orderLineFacade = new InjectionToken<BaseEntityFacade<BaseEntity>>('ORDER_LINE_FACADE');

    const route = entityScreenRoute({ entityName: 'Order', screens: screensOf([{ entityName: 'Order Line', facade: orderLineFacade }]), hostPath: 'order-list' });
    const details = entityGroupOf(route)?.children?.find((child) => child.path === ':entityId/details');
    const embedded = (await details?.loadChildren?.()) as Routes;

    expect(embedded.map((child) => child.path)).toEqual(['order-line']);
  });

  describe('for an entity nothing resolved', () => {
    /**
     * The link that leads here has to render something, and whatever named the entity — an `AppDefinition`
     * route, a hand-written one — is allowed to be ahead of what is deployed.
     */
    it('stays a leaf route, so the component can say so at the URL that was linked', () => {
      const route = entityScreenRoute({ entityName: 'Gone', hostPath: 'order-list' });

      expect(route.component).toBe(BaseEntityScreensComponent);
      expect(route.children).toBeUndefined();
      expect(route.data?.[ENTITY_DESCRIPTOR_ROUTE_DATA_KEY]).toBeUndefined();
      expect(route.data?.[REQUESTED_ENTITY_ROUTE_DATA_KEY]).toBe('Gone');
    });

    it('stays a leaf when no entity is named at all', () => {
      expect(entityScreenRoute({ entityName: undefined, screens: screensOf() }).children).toBeUndefined();
    });
  });
});
