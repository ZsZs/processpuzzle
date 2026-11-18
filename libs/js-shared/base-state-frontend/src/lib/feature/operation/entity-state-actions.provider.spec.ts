import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { HttpErrorResponse } from '@angular/common/http';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { firstValueFrom, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { GovernedEntityRegistry } from '../../domain/definition/governed-entity.registry';
import { State, StateMachineDefinition, Transition } from '../../domain/definition/state-machine-definition';
import { EntityObjectStateService } from '../../domain/operation/entity-object-state.service';
import { BaseStateEntityStateActions } from './entity-state-actions.provider';

const machine = new StateMachineDefinition({
  entityName: 'race',
  stateAttributeKey: 'raceStatus',
  states: [new State({ key: 'PLANNED', name: 'Planned' }), new State({ key: 'RUNNING', name: 'Running' })],
  transitions: [new Transition({ key: 't-start', name: 'Start race', sourceStateKey: 'PLANNED', targetStateKey: 'RUNNING', triggerKey: 'start' })],
});

describe('BaseStateEntityStateActions', () => {
  const governed = { machineFor: vi.fn() };
  const objectStates = { findState: vi.fn(), fireTransition: vi.fn() };

  beforeEach(async () => {
    governed.machineFor.mockReset().mockResolvedValue(machine);
    objectStates.findState.mockReset();
    objectStates.fireTransition.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideTranslocoTesting({
          translations: { en: { 'base_state.state_control.fired': 'State changed to {{state}}.', 'base_state.state_control.rejected': 'The transition was refused: {{reason}}' } },
        }),
        { provide: GovernedEntityRegistry, useValue: governed },
        { provide: EntityObjectStateService, useValue: objectStates },
      ],
    });
    // Loaded up front, so the first translation already reads as text rather than as its key.
    await firstValueFrom(TestBed.inject(TranslocoService).load('en'));
  });

  const actions = () => TestBed.inject(BaseStateEntityStateActions);

  it("describes the state by the machine's names, and the transitions by their own name or trigger", async () => {
    objectStates.findState.mockReturnValue(
      of({
        objectId: 'r1',
        entityName: 'race',
        currentStateKey: 'PLANNED',
        isFinal: false,
        availableTransitions: [
          { transitionKey: 't-start', triggerKey: 'start', targetStateKey: 'RUNNING', guardsSatisfied: true },
          { transitionKey: 'unnamed', triggerKey: 'abort', targetStateKey: 'PLANNED', guardsSatisfied: false, blockedReason: 'Too late' },
        ],
      }),
    );

    expect(await actions().stateOf('Race', 'r1')).toEqual({
      stateAttribute: 'raceStatus',
      currentKey: 'PLANNED',
      currentLabel: 'Planned',
      transitions: [
        { key: 'start', label: 'Start race', enabled: true, blockedReason: undefined },
        { key: 'abort', label: 'abort', enabled: false, blockedReason: 'Too late' },
      ],
    });
    expect(objectStates.findState).toHaveBeenCalledWith('race', 'r1');
  });

  it('knows nothing of an entity no machine governs, nor of an object that has no state yet', async () => {
    governed.machineFor.mockResolvedValue(undefined);
    expect(await actions().stateOf('Order', 'o1')).toBeUndefined();

    governed.machineFor.mockResolvedValue(machine);
    objectStates.findState.mockReturnValue(of(undefined));
    expect(await actions().stateOf('Race', 'r1')).toBeUndefined();
  });

  it("fires through the machine's own entity name and reports the new state", async () => {
    objectStates.fireTransition.mockReturnValue(of({ success: true, previousStateKey: 'PLANNED', newStateKey: 'RUNNING', executedActions: [] }));

    const result = await actions().fire('Race', 'r1', 'start', 4);

    expect(objectStates.fireTransition).toHaveBeenCalledWith('race', 'r1', 'start', 4);
    expect(result.success).toBe(true);
    expect(result.message).toContain('Running');
  });

  it('reports a refusal by a guard, and a failed call, as not successful', async () => {
    objectStates.fireTransition.mockReturnValueOnce(of({ success: false, previousStateKey: 'PLANNED', executedActions: [], rejectionReason: 'No entries' }));
    const refused = await actions().fire('Race', 'r1', 'start', 4);
    expect(refused.success).toBe(false);
    expect(refused.message).toContain('No entries');

    objectStates.fireTransition.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 409 })));
    expect((await actions().fire('Race', 'r1', 'start', 3)).success).toBe(false);
  });
});
