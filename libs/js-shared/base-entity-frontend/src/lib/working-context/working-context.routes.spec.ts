import { Component } from '@angular/core';
import { Route, UrlSegment } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { BaseEntityFacade } from '../base-entity-facade/base-entity-facade';
import { workingContextRoutes } from './working-context.routes';

@Component({ selector: 'pp-view-stub', template: 'view' })
class ViewStubComponent {}

abstract class ZoneFacadeToken extends BaseEntityFacade<never> {}

const segments = (...paths: string[]) => paths.map((path) => ({ path }) as UrlSegment);
const canMatch = (route: Route, ...paths: string[]) => (route.canMatch?.[0] as unknown as (route: Route, segments: UrlSegment[]) => boolean)(route, segments(...paths));

describe('workingContextRoutes — the aggregate of the context', () => {
  const views = [{ path: 'zones', component: ViewStubComponent }];
  const aggregate = { entityName: 'Warehouse', embeddedChildren: [{ entityName: 'Zone', facade: ZoneFacadeToken as never }] };

  it("makes the context's route a level of the entity chain, by naming its entity", () => {
    const [withEntity] = workingContextRoutes('warehouse', views, aggregate);

    expect(withEntity.data).toEqual({ workingContext: 'warehouse', entityName: 'Warehouse' });
  });

  it('leaves the route without an entity name when it has none to give, as before', () => {
    const [plain] = workingContextRoutes('warehouse', views);

    expect(plain.data).toEqual({ workingContext: 'warehouse' });
  });

  it('mounts the embedded child branches next to the views, the screens of the rows below the context', () => {
    const [withEntity] = workingContextRoutes('warehouse', views, aggregate);

    expect((withEntity.children ?? []).map((child) => child.path)).toEqual(['', 'zones', 'zone']);
    const branch = withEntity.children?.find((child) => child.path === 'zone');
    expect(branch?.data).toMatchObject({ entityName: 'Zone', embeddedEntity: true });
  });

  it('does not take the name of an embedded branch for the id of a context', () => {
    const [withEntity] = workingContextRoutes('warehouse', views, aggregate);

    expect(canMatch(withEntity, 'ctx', 'warehouse', 'zone')).toBe(false);
    expect(canMatch(withEntity, 'ctx', 'warehouse', 'zones')).toBe(false);
    expect(canMatch(withEntity, 'ctx', 'warehouse', 'W1')).toBe(true);
  });

  it('lands on the first view, not on an embedded branch', () => {
    const [withEntity] = workingContextRoutes('warehouse', views, aggregate);

    expect(withEntity.children?.[0]).toMatchObject({ path: '', redirectTo: 'zones' });
  });
});
