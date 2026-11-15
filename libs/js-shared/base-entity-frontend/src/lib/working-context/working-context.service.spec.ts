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
import { provideWorkingContexts, WorkingContextDefinition } from './working-context.definition';
import { workingContextRoutes } from './working-context.routes';
import { WorkingContextService } from './working-context.service';

@Component({ selector: 'pp-view-stub', template: 'view' })
class ViewStubComponent {}

const RACES = [
  { id: 'r1', name: 'Spring Cup', status: 'Planned' },
  { id: 'r2', name: 'Summer Cup', status: 'Running' },
  { id: 'r3', name: 'Autumn Cup', status: 'Planned' },
];

/** The context entity's facade: its descriptor, and a store that holds some races and can load the rest. */
class RaceFacadeStub {
  readonly store = {
    entities: signal(RACES.slice(0, 2)),
    reload: vi.fn(async (id: string) => {
      const found = RACES.find((race) => race.id === id);
      if (found) this.store.entities.update((entities) => [...entities, found]);
      return found;
    }),
    load: vi.fn(),
  };
  readonly descriptor = new BaseEntityDescriptor({
    attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true), new BaseEntityAttrDescriptor('status', FormControlType.STATE, 'Status')],
    entityName: 'Race',
    store: this.store,
  });
}

const RACE_CONTEXT: WorkingContextDefinition = { name: 'race', entityName: 'Race', basePath: '/conduct', confirmSwitchWhen: { state: 'Running' } };
const HEAT_CONTEXT: WorkingContextDefinition = { name: 'heat', entityName: 'Race', parent: 'race' };

const ROUTES = [
  {
    path: 'conduct',
    children: workingContextRoutes('race', [
      { path: 'participants', component: ViewStubComponent },
      { path: 'rounds', component: ViewStubComponent },
      ...workingContextRoutes('heat', [{ path: 'scores', component: ViewStubComponent }]),
    ]),
  },
  { path: 'home', component: ViewStubComponent },
];

describe('WorkingContextService', () => {
  let confirmAnswer: boolean;

  beforeEach(() => {
    localStorage.clear();
    confirmAnswer = true;
    TestBed.configureTestingModule({
      providers: [
        provideRouter(ROUTES),
        provideTranslocoTesting({ translations: {} }),
        provideWorkingContexts(RACE_CONTEXT, HEAT_CONTEXT),
        RaceFacadeStub,
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { Race: RaceFacadeStub } },
        { provide: MatDialog, useValue: { open: vi.fn(() => ({ afterClosed: () => of(confirmAnswer) })) } },
      ],
    });
  });

  const url = () => TestBed.inject(Router).url;
  const service = () => TestBed.inject(WorkingContextService);

  it('reads the active context and its entity from the URL', async () => {
    await RouterTestingHarness.create('/conduct/ctx/race/r1/participants');

    expect(url()).toBe('/conduct/ctx/race/r1/participants');
    expect(
      service()
        .active()
        .map(({ name, id, entity }) => ({ name, id, entity })),
    ).toEqual([{ name: 'race', id: 'r1', entity: RACES[0] }]);
    expect(service().current('race')?.url).toBe('/conduct/ctx/race/r1');
  });

  it('loads an entity its store does not hold yet', async () => {
    await RouterTestingHarness.create('/conduct/ctx/race/r3/rounds');

    expect(TestBed.inject(RaceFacadeStub).store.reload).toHaveBeenCalledWith('r3');
    expect(service().current('race')?.entity).toEqual(RACES[2]);
  });

  it('lands a bare context URL on the first view', async () => {
    await RouterTestingHarness.create('/conduct/ctx/race/r1');

    expect(url()).toBe('/conduct/ctx/race/r1/participants');
  });

  it('shows the empty state rather than a race named like a view, and without a context', async () => {
    const harness = await RouterTestingHarness.create('/conduct/ctx/race/participants');

    expect(url()).toBe('/conduct/ctx/race/participants');
    expect(harness.routeNativeElement?.querySelector('[data-testid="working-context-empty-state"]')).not.toBeNull();
    expect(service().active()).toEqual([]);
  });

  it('falls back to the empty state for an id that does not resolve, and forgets it', async () => {
    localStorage.setItem('pp.workingContext.race.last', 'gone');

    await RouterTestingHarness.create('/conduct/ctx/race/gone/rounds');

    expect(url()).toBe('/conduct/ctx/race/rounds');
    expect(service().lastId('race')).toBeUndefined();
  });

  it('restores the last active entity on a context view opened without one', async () => {
    localStorage.setItem('pp.workingContext.race.last', 'r1');

    await RouterTestingHarness.create('/conduct/ctx/race/rounds');

    expect(url()).toBe('/conduct/ctx/race/r1/rounds');
  });

  it('does not restore when the context persists to the URL only', async () => {
    service().register([{ ...RACE_CONTEXT, persist: 'url' }]);
    localStorage.setItem('pp.workingContext.race.last', 'r1');

    await RouterTestingHarness.create('/conduct/ctx/race/rounds');

    expect(url()).toBe('/conduct/ctx/race/rounds');
  });

  it('selects from outside the context onto its first view, remembering it as recent', async () => {
    await RouterTestingHarness.create('/home');

    expect(await service().select('race', 'r1')).toBe(true);

    expect(url()).toBe('/conduct/ctx/race/r1/participants');
    expect(service().recent('race')).toEqual(['r1']);
  });

  it('selects from the empty state onto the view that was asked for', async () => {
    await RouterTestingHarness.create('/conduct/ctx/race/rounds');

    await service().select('race', 'r3');

    expect(url()).toBe('/conduct/ctx/race/r3/rounds');
  });

  it('switches on the same view, dropping what addressed the entity being left', async () => {
    await RouterTestingHarness.create('/conduct/ctx/race/r1/ctx/heat/r3/scores');

    await service().select('race', 'r3');

    expect(url()).toBe('/conduct/ctx/race/r3/participants');
    expect(service().current('heat')).toBeUndefined();
  });

  it('asks before switching away from an entity matching confirmSwitchWhen, and stays when declined', async () => {
    await RouterTestingHarness.create('/conduct/ctx/race/r2/rounds');
    confirmAnswer = false;

    expect(await service().select('race', 'r1')).toBe(false);
    expect(url()).toBe('/conduct/ctx/race/r2/rounds');
    expect(TestBed.inject(MatDialog).open).toHaveBeenCalledTimes(1);

    confirmAnswer = true;
    expect(await service().select('race', 'r1')).toBe(true);
    expect(url()).toBe('/conduct/ctx/race/r1/rounds');
  });

  it('does not ask when the active entity does not match', async () => {
    await RouterTestingHarness.create('/conduct/ctx/race/r1/rounds');

    await service().select('race', 'r3');

    expect(TestBed.inject(MatDialog).open).not.toHaveBeenCalled();
  });

  it('selects a child context below its active parent, and not without one', async () => {
    await RouterTestingHarness.create('/home');
    expect(await service().select('heat', 'r3')).toBe(false);

    await service().select('race', 'r1');
    await service().select('heat', 'r3');

    expect(url()).toBe('/conduct/ctx/race/r1/ctx/heat/r3/scores');
    expect(
      service()
        .active()
        .map((context) => context.name),
    ).toEqual(['race', 'heat']);
  });

  it('clears into the empty state of the same view and drops the fallback', async () => {
    await RouterTestingHarness.create('/conduct/ctx/race/r1/rounds');

    await service().clear('race');

    expect(url()).toBe('/conduct/ctx/race/rounds');
    expect(service().active()).toEqual([]);
    expect(service().lastId('race')).toBeUndefined();
  });

  it('offers recent ids most recent first, up to the declared limit', async () => {
    service().remember('race', 'r1');
    service().remember('race', 'r2');
    service().remember('race', 'r3');
    service().remember('race', 'r1');
    service().register([{ ...RACE_CONTEXT, picker: { recent: 2 } }]);

    expect(service().recent('race')).toEqual(['r1', 'r3']);
  });
});
