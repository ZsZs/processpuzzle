import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { By } from '@angular/platform-browser';
import { BaseEntity, BaseEntityAttrDescriptor, BaseEntityDescriptorRegistry, BaseFormNavigatorSingletonStore, FormControlType } from '@processpuzzle/base-entity';
import { TranslocoService } from '@jsverse/transloco';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { NGXLogger } from 'ngx-logging-kit';
import { firstValueFrom, Observable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { GovernedEntityRegistry } from '../../domain/definition/governed-entity.registry';
import { StateMachineDefinition } from '../../domain/definition/state-machine-definition';
import { StateMachineDefinitionMapper } from '../../domain/definition/state-machine-definition.mapper';
import { STATE_MACHINE_DEFINITION_DTO } from '../../domain/definition/test-state-machine-definition';
import { AvailableTransition, EntityObjectState, TransitionResult } from '../../domain/operation/entity-object-state';
import { EntityObjectStateService } from '../../domain/operation/entity-object-state.service';
import { EntityStateControlComponent } from './entity-state-control.component';

interface Order extends BaseEntity {
  id?: string;
  status?: string;
  version?: number;
}

/**
 * The STATE control of an `Order`'s Details form. The machine registry and the operation service are stubbed —
 * each has a spec of its own — so what is asserted is what the control shows and the order it calls them in
 * when a transition is picked.
 *
 * Translations are flat dotted keys, for the reason given in `entity-state-machine-tab.component.spec.ts`.
 */
describe('EntityStateControlComponent', () => {
  const machine = new StateMachineDefinitionMapper().fromDto(STATE_MACHINE_DEFINITION_DTO);
  const objectId = '46ecc74f-6bc2-4282-9a4f-58ab0e259c28';

  const confirm: AvailableTransition = { transitionKey: 'confirm', triggerKey: 'confirm', targetStateKey: 'DELIVERED', guardsSatisfied: true };
  const blocked: AvailableTransition = { transitionKey: 'cancel', triggerKey: 'cancel', targetStateKey: 'DRAFT', guardsSatisfied: false, blockedReason: 'already paid' };
  const objectState = (availableTransitions: AvailableTransition[] = [confirm, blocked]): EntityObjectState => ({
    objectId,
    entityName: 'order',
    currentStateKey: 'DRAFT',
    isFinal: false,
    availableTransitions,
  });

  const translations = {
    en: {
      'base_state.state_control.placeholder': 'Change state…',
      'base_state.state_control.noTransitions': 'No transition is available from this state.',
      'base_state.state_control.transitionTo': '→ {{state}}',
      'base_state.state_control.via': '({{trigger}})',
      'base_state.state_control.blocked': 'Not allowed: {{reason}}',
      'base_state.state_control.blockedNoReason': 'Not allowed by a guard of this transition.',
      'base_state.state_control.rejected': 'The transition was refused: {{reason}}',
      'base_state.state_control.fired': 'State changed to {{state}}.',
      'base_state.state_control.initialState': 'Starts in {{state}}',
    },
  };

  let fixture: ComponentFixture<EntityStateControlComponent<Order>>;
  let stateService: { findState: ReturnType<typeof vi.fn>; fireTransition: ReturnType<typeof vi.fn> };
  let snackBar: { open: ReturnType<typeof vi.fn> };
  let store: { reload: ReturnType<typeof vi.fn> };
  let logger: { warn: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  interface Setup {
    governedMachine?: StateMachineDefinition;
    state?: EntityObjectState;
    entity?: Order;
    fire?: () => Observable<TransitionResult>;
  }

  const select = () => fixture.debugElement.query(By.directive(MatSelect));
  const matSelect = () => select().componentInstance as MatSelect;

  /** `in` rather than defaults, so an explicitly passed `undefined` means "nothing", as in the tab's spec. */
  async function render(setup: Setup = {}) {
    const governedMachine = 'governedMachine' in setup ? setup.governedMachine : machine;
    const state = 'state' in setup ? setup.state : objectState();
    const entity: Order = setup.entity ?? { id: objectId, status: 'DRAFT', version: 7 };

    stateService = {
      findState: vi.fn(() => of(state)),
      fireTransition: vi.fn(setup.fire ?? (() => of({ success: true, previousStateKey: 'DRAFT', newStateKey: 'DELIVERED', executedActions: [], version: 8 }))),
    };
    snackBar = { open: vi.fn() };
    store = { reload: vi.fn(async () => undefined) };
    logger = { warn: vi.fn(), error: vi.fn() };

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [EntityStateControlComponent],
      providers: [
        provideTranslocoTesting({ translations }),
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: NGXLogger, useValue: logger },
        { provide: BaseFormNavigatorSingletonStore, useValue: {} },
        { provide: BaseEntityDescriptorRegistry, useValue: {} },
        { provide: GovernedEntityRegistry, useValue: { machineFor: vi.fn(async () => governedMachine) } },
        { provide: EntityObjectStateService, useValue: stateService },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();

    // Loaded up front, so the first render already shows translated text rather than keys.
    await firstValueFrom(TestBed.inject(TranslocoService).load('en'));
    fixture = TestBed.createComponent(EntityStateControlComponent<Order>);
    fixture.componentRef.setInput('config', new BaseEntityAttrDescriptor('status', FormControlType.STATE, 'Status'));
    fixture.componentRef.setInput('entity', entity);
    fixture.componentRef.setInput('entityName', 'Order');
    fixture.componentRef.setInput('value', entity.status);
    fixture.componentInstance.store = store as unknown as EntityStateControlComponent<Order>['store'];
    fixture.componentInstance.formGroup = new FormGroup({ status: new FormControl({ value: entity.status, disabled: true }) });
    await settle();
  }

  /**
   * Initialization awaits two collaborators, which `whenStable` does not track — so each round yields a
   * macrotask too, letting those promises settle before the next change detection reads them.
   */
  async function settle(): Promise<void> {
    for (let attempt = 0; attempt < 5; attempt++) {
      fixture.detectChanges();
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve));
    }
  }

  async function fire(transition: AvailableTransition): Promise<void> {
    select().triggerEventHandler('selectionChange', { value: transition, source: matSelect() });
    await settle();
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('loads the machine and the object state, and shows the current state by name', async () => {
    await render();

    expect(stateService.findState).toHaveBeenCalledWith('order', objectId);
    expect(matSelect().placeholder).toBe('Draft');
    expect(matSelect().disabled).toBe(false);
  });

  it('returns void from the initialization hook while loading state asynchronously', async () => {
    await render();

    expect(fixture.componentInstance.ngOnInit()).toBeUndefined();
    await settle();

    expect(matSelect().placeholder).toBe('Draft');
  });

  it('reports initialization failures without an unhandled rejection', async () => {
    await render();
    const error = new Error('Machine lookup failed');
    vi.mocked(TestBed.inject(GovernedEntityRegistry).machineFor).mockRejectedValueOnce(error);

    expect(fixture.componentInstance.ngOnInit()).toBeUndefined();
    await settle();

    expect(logger.warn).toHaveBeenCalledWith('EntityStateControlComponent failed to load state', { error });
  });

  it('shows the initial state of the machine for a record not saved yet, without asking for its state', async () => {
    await render({ entity: { status: undefined } });

    expect(stateService.findState).not.toHaveBeenCalled();
    expect(matSelect().placeholder).toBe('Starts in Draft');
    expect(matSelect().disabled).toBe(true);
  });

  it("falls back to the attribute's own value when the operation endpoint knows no state", async () => {
    await render({ state: undefined });

    expect(matSelect().placeholder).toBe('Draft');
    expect(matSelect().disabled).toBe(true);
  });

  it('lists blocked transitions disabled, with the reason', async () => {
    await render();

    matSelect().open();
    await settle();

    const options = matSelect().options.toArray();
    expect(options.map((option) => option.disabled)).toEqual([false, true]);
    expect(options[0].viewValue).toContain('→ Delivered');
    expect(options[1].viewValue).toContain('Not allowed: already paid');
  });

  it('cannot be opened when every transition is blocked', async () => {
    await render({ state: objectState([blocked]) });

    expect(matSelect().disabled).toBe(true);
  });

  it('says so when no transition leaves the current state', async () => {
    await render({ state: objectState([]) });

    expect(fixture.nativeElement.textContent).toContain('No transition is available from this state.');
  });

  it("fires the trigger with the entity's version, reports the new state, then reloads the record", async () => {
    await render();

    await fire(confirm);

    expect(stateService.fireTransition).toHaveBeenCalledWith('order', objectId, 'confirm', 7);
    expect(snackBar.open).toHaveBeenCalledWith('State changed to Delivered.', undefined, { duration: 4000 });
    expect(store.reload).toHaveBeenCalledWith(objectId);
    expect(snackBar.open.mock.invocationCallOrder[0]).toBeLessThan(store.reload.mock.invocationCallOrder[0]);
  });

  it('reports a refusing guard and still reloads', async () => {
    await render({ fire: () => of({ success: false, previousStateKey: 'DRAFT', rejectionReason: 'insufficient balance', executedActions: [] }) });

    await fire(confirm);

    expect(snackBar.open).toHaveBeenCalledWith('The transition was refused: insufficient balance', undefined, { duration: 4000 });
    expect(store.reload).toHaveBeenCalledWith(objectId);
  });

  it('still reloads the record when firing fails', async () => {
    await render({ fire: () => throwError(() => new Error('409 Conflict')) });

    await fire(confirm);

    expect(snackBar.open).not.toHaveBeenCalled();
    expect(logger.warn).toHaveBeenCalled();
    expect(store.reload).toHaveBeenCalledWith(objectId);
  });

  it('fires nothing for an empty selection', async () => {
    await render();

    await fire(null as unknown as AvailableTransition);

    expect(stateService.fireTransition).not.toHaveBeenCalled();
    expect(store.reload).not.toHaveBeenCalled();
  });
});
