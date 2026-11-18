import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from '../base-entity-facade/base-entity-facade-registry';
import { Component } from '@angular/core';
import { ENTITY_STATE_ACTIONS, EntityStateSnapshot } from './entity-state-actions';
import { provideWorkingContexts } from './working-context.definition';
import { workingContextRoutes } from './working-context.routes';
import { WorkingContextService } from './working-context.service';

@Component({ selector: 'pp-view-stub', template: 'view' })
class ViewStubComponent {}

const running: EntityStateSnapshot = {
  stateAttribute: 'raceStatus',
  currentKey: 'RUNNING',
  currentLabel: 'Running',
  transitions: [{ key: 'finish', label: 'Finish race', enabled: true }],
};

class RaceFacadeStub {
  readonly rows = signal([{ id: 'r1', name: 'Spring Cup', version: 3, raceStatus: 'RUNNING' }]);
  readonly store = {
    entities: this.rows,
    reload: vi.fn(async () => {
      this.rows.update((rows) => rows.map((row) => ({ ...row, version: row.version + 1, raceStatus: 'FINISHED' })));
      return this.rows()[0];
    }),
    load: vi.fn(),
  };
  // No STATE attribute marked: the descriptor alone cannot tell which attribute holds the state.
  readonly descriptor = new BaseEntityDescriptor({ attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true)], entityName: 'Race', store: this.store });
}

describe('WorkingContextService — state', () => {
  const stateActions = { stateOf: vi.fn(), fire: vi.fn() };
  const snackBar = { open: vi.fn() };
  let confirmAnswer: boolean;

  beforeEach(() => {
    confirmAnswer = true;
    stateActions.stateOf.mockReset().mockResolvedValue(running);
    stateActions.fire.mockReset().mockResolvedValue({ success: true, message: 'State changed to Finished.' });
    snackBar.open.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'conduct', children: workingContextRoutes('race', [{ path: 'rounds', component: ViewStubComponent }]) },
          { path: 'home', component: ViewStubComponent },
        ]),
        provideTranslocoTesting({ translations: {} }),
        provideWorkingContexts({ name: 'race', entityName: 'Race', basePath: '/conduct', confirmSwitchWhen: { state: 'RUNNING' } }),
        RaceFacadeStub,
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { Race: RaceFacadeStub } },
        { provide: ENTITY_STATE_ACTIONS, useValue: stateActions },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: MatDialog, useValue: { open: vi.fn(() => ({ afterClosed: () => of(confirmAnswer) })) } },
      ],
    });
  });

  const service = () => TestBed.inject(WorkingContextService);

  async function activate() {
    await RouterTestingHarness.create('/conduct/ctx/race/r1/rounds');
    TestBed.flushEffects();
    await vi.waitFor(() => expect(service().stateOf('race')).toBeDefined());
  }

  it("knows the active entity's state and transitions once it is active", async () => {
    await activate();

    expect(stateActions.stateOf).toHaveBeenCalledWith('Race', 'r1');
    expect(service().stateOf('race')?.currentLabel).toBe('Running');
    expect(
      service()
        .stateOf('race')
        ?.transitions.map((transition) => transition.key),
    ).toEqual(['finish']);
  });

  it('knows nothing of an entity no state machine governs', async () => {
    stateActions.stateOf.mockResolvedValue(undefined);

    await RouterTestingHarness.create('/conduct/ctx/race/r1/rounds');
    TestBed.flushEffects();
    await vi.waitFor(() => expect(stateActions.stateOf).toHaveBeenCalled());

    expect(service().stateOf('race')).toBeUndefined();
  });

  it('does not ask again for the same version of the same entity', async () => {
    await activate();
    TestBed.flushEffects();

    expect(stateActions.stateOf).toHaveBeenCalledTimes(1);
  });

  it('asks the machine for the attribute that holds the state, when confirming a switch', async () => {
    await activate();
    confirmAnswer = false;

    // Without the machine's word the `state` condition could not be resolved on this descriptor, and no question would be asked.
    expect(await service().select('race', 'other')).toBe(false);
    expect(TestBed.inject(MatDialog).open).toHaveBeenCalledTimes(1);
  });

  it('fires a transition on the active entity, says what came of it and reloads the entity', async () => {
    await activate();

    expect(await service().fire('race', 'finish')).toBe(true);

    expect(stateActions.fire).toHaveBeenCalledWith('Race', 'r1', 'finish', 3);
    expect(snackBar.open).toHaveBeenCalledWith('State changed to Finished.', undefined, expect.anything());
    expect(TestBed.inject(RaceFacadeStub).store.reload).toHaveBeenCalledWith('r1');
  });

  it('refreshes the state after a transition: the reloaded entity has a new version', async () => {
    await activate();
    stateActions.stateOf.mockResolvedValue({ ...running, currentKey: 'FINISHED', currentLabel: 'Finished', transitions: [] });

    await service().fire('race', 'finish');
    TestBed.flushEffects();

    await vi.waitFor(() => expect(service().stateOf('race')?.currentLabel).toBe('Finished'));
  });

  it('reports a refusal without claiming success', async () => {
    await activate();
    stateActions.fire.mockResolvedValue({ success: false, message: 'The transition was refused: no entries.' });

    expect(await service().fire('race', 'finish')).toBe(false);
    expect(snackBar.open).toHaveBeenCalledWith('The transition was refused: no entries.', undefined, expect.anything());
  });

  it('does nothing without an active context', async () => {
    await RouterTestingHarness.create('/home');

    expect(await service().fire('race', 'finish')).toBe(false);
    expect(stateActions.fire).not.toHaveBeenCalled();
  });
});
