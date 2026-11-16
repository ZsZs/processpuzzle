import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from '../base-entity-facade/base-entity-facade-registry';
import { EntityRouteRegistry } from '../base-form-navigator/entity-route.registry';
import { contextObjectGuard, CONTEXT_OBJECT_ENTITY_ROUTE_DATA_KEY } from './context-object.guard';
import { WorkingContextService } from './working-context.service';
import { WORKING_CONTEXT_ROUTE_DATA_KEY } from './working-context.url';

@Component({ selector: 'pp-view-stub', template: 'view' })
class ViewStubComponent {}

const RACES = [
  { id: 'r1', name: 'Spring Cup' },
  { id: 'r2', name: 'Summer Cup' },
];

class RaceFacadeStub {
  readonly store = {
    entities: signal(RACES.slice(0, 1)),
    reload: vi.fn(async (id: string) => {
      const found = RACES.find((race) => race.id === id);
      if (found) this.store.entities.update((entities) => (entities.some((entity) => entity.id === id) ? entities : [...entities, found]));
      return found;
    }),
    load: vi.fn(),
  };
  readonly descriptor = new BaseEntityDescriptor({
    attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true)],
    entityName: 'Race',
    canContextObject: true,
    store: this.store,
  });
}

/** An aggregate's routes as `baseEntityRoutes` mounts them for an entity that canContextObject, without the embedded screens. */
const ROUTES = [
  {
    path: 'races',
    data: { entityName: 'Race' },
    children: [
      { path: 'list', component: ViewStubComponent },
      {
        path: ':entityId',
        data: { [WORKING_CONTEXT_ROUTE_DATA_KEY]: 'race', [CONTEXT_OBJECT_ENTITY_ROUTE_DATA_KEY]: 'Race' },
        canActivate: [contextObjectGuard],
        children: [
          { path: '', pathMatch: 'full' as const, redirectTo: 'rounds/list' },
          { path: 'rounds/list', component: ViewStubComponent },
          { path: 'scores/list', component: ViewStubComponent },
        ],
      },
    ],
  },
];

describe('a context object: the aggregate as its own context', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(ROUTES),
        provideTranslocoTesting({ translations: {} }),
        RaceFacadeStub,
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { Race: RaceFacadeStub } },
        { provide: MatDialog, useValue: { open: vi.fn(() => ({ afterClosed: () => of(true) })) } },
      ],
    });
    TestBed.inject(EntityRouteRegistry).scan();
  });

  const url = () => TestBed.inject(Router).url;
  const service = () => TestBed.inject(WorkingContextService);

  it('makes the object in the URL the context, and remembers it', async () => {
    await RouterTestingHarness.create('/races/r1/scores/list');

    expect(url()).toBe('/races/r1/scores/list');
    expect(service().current('race')).toMatchObject({ id: 'r1', url: '/races/r1' });
    expect(service().definitionOf('race')).toMatchObject({ entityName: 'Race', aggregate: true });
    expect(service().lastId('race')).toBe('r1');
  });

  it('lands the aggregate on its first embedded list', async () => {
    await RouterTestingHarness.create('/races/r1');

    expect(url()).toBe('/races/r1/rounds/list');
  });

  it('resolves "unknown" to the remembered object', async () => {
    service().remember('race', 'r2');
    service().ensureAggregate('Race');

    await RouterTestingHarness.create('/races/unknown/scores/list');

    expect(url()).toBe('/races/r2/scores/list');
    expect(service().current('race')?.id).toBe('r2');
  });

  it('sends "unknown" with nothing remembered to the list, and continues to the page asked for once one is chosen', async () => {
    await RouterTestingHarness.create('/races/unknown/scores/list');

    expect(url()).toBe('/races/list');
    expect(service().current('race')).toBeUndefined();
    expect(service().pendingTarget('race')).toBe('/races/unknown/scores/list');

    expect(await service().select('race', 'r2')).toBe(true);

    expect(url()).toBe('/races/r2/scores/list');
    expect(service().pendingTarget('race')).toBeUndefined();
  });

  it('forgets an id that no longer resolves and treats it as unknown', async () => {
    service().remember('race', 'gone');
    service().ensureAggregate('Race');

    await RouterTestingHarness.create('/races/gone/rounds/list');

    expect(url()).toBe('/races/list');
    expect(service().lastId('race')).toBeUndefined();
  });

  it('selects from outside onto the aggregate', async () => {
    await RouterTestingHarness.create('/races/list');
    service().ensureAggregate('Race');

    await service().select('race', 'r1');

    expect(url()).toBe('/races/r1/rounds/list');
  });

  it('switches to another object on the same kind of part', async () => {
    await RouterTestingHarness.create('/races/r1/scores/list');

    await service().select('race', 'r2');

    expect(url()).toBe('/races/r2/scores/list');
  });

  it('clears into the same view, which asks for an object again', async () => {
    await RouterTestingHarness.create('/races/r1/scores/list');

    await service().clear('race');

    expect(url()).toBe('/races/list');
    expect(service().lastId('race')).toBeUndefined();
    expect(service().pendingTarget('race')).toBe('/races/unknown/scores/list');
  });
});
