import type { ActivatedRouteSnapshot } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { listQueryOf, routeListFilterOf } from './route-list-filter';

/** A route snapshot declaring `data`, under `parent`: only the parts the helper reads. */
function route(data: Record<string, unknown> | undefined, parent: ActivatedRouteSnapshot | null = null): ActivatedRouteSnapshot {
  return { routeConfig: { data }, parent } as unknown as ActivatedRouteSnapshot;
}

describe('routeListFilterOf', () => {
  it('reads what the screens route of the entity declares', () => {
    const screens = route({ requestedEntityName: 'Result', rsqlFilter: ' status!=CLOSED ', filterByContext: 'race' });
    const list = route(undefined, screens);

    expect(routeListFilterOf(list, 'Result')).toEqual({ rsqlFilter: 'status!=CLOSED', filterByContext: 'race' });
  });

  it('does not take the filters of a screens route for another entity', () => {
    const screens = route({ requestedEntityName: 'Result', filterByContext: 'race' });

    expect(routeListFilterOf(route(undefined, screens), 'Round')).toEqual({});
  });

  it('reads only what a route declares itself, not what it inherits from a parent', () => {
    const outer = route({ requestedEntityName: 'Result', filterByContext: 'race' });
    const inner = route({ requestedEntityName: 'Result' }, outer);

    expect(routeListFilterOf(inner, 'Result')).toEqual({ filterByContext: undefined, rsqlFilter: undefined });
  });

  it('has no filters for a list reached without a screens route, or with empty ones', () => {
    expect(routeListFilterOf(null, 'Result')).toEqual({});
    expect(routeListFilterOf(route({ requestedEntityName: 'Result', rsqlFilter: '  ' }), 'Result')).toEqual({ rsqlFilter: undefined, filterByContext: undefined });
  });
});

describe('listQueryOf', () => {
  it('is undefined for a plain list', () => {
    expect(listQueryOf({}, undefined, true)).toBeUndefined();
  });

  it('is the route filter alone when no context applies', () => {
    expect(listQueryOf({ rsqlFilter: 'status!=CLOSED', filterByContext: 'race' }, undefined, true)).toBe('status!=CLOSED');
    expect(listQueryOf({ rsqlFilter: 'status!=CLOSED', filterByContext: 'race' }, 'r1', false)).toBe('status!=CLOSED');
  });

  it('narrows to the active entity, joined with the route filter', () => {
    expect(listQueryOf({ rsqlFilter: 'status!=CLOSED', filterByContext: 'race' }, 'r1', true)).toBe('status!=CLOSED;race==r1');
  });

  it('narrows a list that has no route filter of its own', () => {
    expect(listQueryOf({ filterByContext: 'race' }, 'r1', true)).toBe('race==r1');
  });
});
