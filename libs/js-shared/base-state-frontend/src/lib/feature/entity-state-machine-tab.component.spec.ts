import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ROUTER_OUTLET_DATA } from '@angular/router';
import { BaseEntityAttrDescriptor, BaseEntityDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { GovernedEntityRegistry } from '../domain/governed-entity.registry';
import { DiagramDefinitionMapper } from '../domain/modeler/data-access/diagram-definition.mapper';
import { DiagramDefinitionService } from '../domain/modeler/data-access/diagram-definition.service';
import { DIAGRAM_DEFINITION_DTO } from '../domain/modeler/models/test-diagram-definition';
import { EntityObjectState } from '../domain/operation/entity-object-state';
import { EntityObjectStateService } from '../domain/operation/entity-object-state.service';
import { StateMachineDefinition } from '../domain/state-machine-definition';
import { StateMachineDefinitionMapper } from '../domain/state-machine-definition.mapper';
import { STATE_MACHINE_DEFINITION_DTO } from '../domain/test-state-machine-definition';
import { StateMachineCanvasComponent } from './modeler/components/state-machine-canvas.component';
import { EntityStateMachineTabComponent } from './entity-state-machine-tab.component';

/**
 * The State Machine tab as a reader of an `Order` sees it: where this order is, drawn on the machine that
 * governs orders, with nothing on the screen that could change either.
 *
 * The three collaborators are stubbed rather than driven through HTTP — each has a spec of its own — so
 * what is asserted here is the *orchestration*: which of them is asked, and what the screen shows when one
 * of them has nothing to give.
 *
 * Translations are given as flat dotted keys: `TestTranslocoLoader` keeps only the non-object entries of a
 * language when the config names no scope, so a nested `{ base_state: { … } }` would be dropped and every
 * assertion would be comparing raw keys.
 */
describe('EntityStateMachineTabComponent', () => {
  const machine = new StateMachineDefinitionMapper().fromDto(STATE_MACHINE_DEFINITION_DTO);
  const layout = new DiagramDefinitionMapper().fromDto(DIAGRAM_DEFINITION_DTO);
  const objectId = '46ecc74f-6bc2-4282-9a4f-58ab0e259c28';

  const objectState = (currentStateKey: string, isFinal = false): EntityObjectState => ({ objectId, entityName: 'order', currentStateKey, isFinal, availableTransitions: [] });

  const orderDescriptor = new BaseEntityDescriptor({
    entityName: 'Order',
    attrDescriptors: [new BaseEntityAttrDescriptor('orderNumber', FormControlType.TEXT_BOX, 'Order #', undefined, true)],
  });

  const translations = {
    en: {
      'base_state.entity_state_machine.currentState': 'Current state',
      'base_state.entity_state_machine.final': 'final',
      'base_state.entity_state_machine.noMachine': 'No state machine governs this entity type.',
      'base_state.entity_state_machine.unknownState': "'{{state}}' is not a state this machine declares.",
      'base_state.entity_state_machine.readOnly': 'Read-only view.',
      'base_state.entity_state_machine.loading': 'Loading…',
      'base_state.entity_state_machine.noState': 'This record has no state yet.',
    },
  };

  let fixture: ComponentFixture<EntityStateMachineTabComponent>;

  const text = () => ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ').trim();
  const canvas = () => fixture.debugElement.query(By.directive(StateMachineCanvasComponent))?.componentInstance as StateMachineCanvasComponent | undefined;

  interface Setup {
    governedMachine?: StateMachineDefinition;
    state?: EntityObjectState;
    descriptor?: BaseEntityDescriptor;
    layoutFails?: boolean;
  }

  /**
   * `in` rather than a destructuring default for the three that may legitimately be absent: a default
   * parameter fires on an explicitly passed `undefined` too, which would silently turn every "nothing to
   * show" test into a repeat of the happy path.
   */
  async function render(setup: Setup = {}) {
    const governedMachine = 'governedMachine' in setup ? setup.governedMachine : machine;
    const state = 'state' in setup ? setup.state : objectState('DELIVERED', true);
    const descriptor = 'descriptor' in setup ? setup.descriptor : orderDescriptor;

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [EntityStateMachineTabComponent],
      providers: [
        provideTranslocoTesting({ translations }),
        { provide: ROUTER_OUTLET_DATA, useValue: signal(descriptor) },
        { provide: GovernedEntityRegistry, useValue: { machineFor: vi.fn(async () => governedMachine) } },
        { provide: DiagramDefinitionService, useValue: { findByEntityName: vi.fn(() => (setup.layoutFails ? throwError(() => new Error('boom')) : of(layout))) } },
        { provide: EntityObjectStateService, useValue: { findState: vi.fn(() => of(state)) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EntityStateMachineTabComponent);
    fixture.componentRef.setInput('entityId', objectId);
    await settle();
  }

  /**
   * Renders until the tab has stopped loading. The effect that starts the load runs on the first change
   * detection and awaits three collaborators, so a single `whenStable` leaves the screen on its loading
   * caption — which is what every assertion below would then be reading.
   */
  async function settle(): Promise<void> {
    for (let attempt = 0; attempt < 20; attempt++) {
      fixture.detectChanges();
      await fixture.whenStable();
      if (!text().includes('Loading')) return;
    }
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('names the state the record is in and draws the machine around it', async () => {
    await render();

    // The state's *name*, not its key — the same rule the node labels follow.
    expect(text()).toContain('Delivered');
    expect(text()).toContain('final');
    expect(canvas()).toBeDefined();
  });

  it('draws the machine read-only, with the current state marked', async () => {
    await render({ state: objectState('DRAFT') });

    // Asserted through the canvas's own inputs: what it does with them is its spec's business.
    expect(canvas()?.readOnly).toBe(true);
    expect(canvas()?.currentStateKey).toBe('DRAFT');
    expect(canvas()?.machine).toBe(machine);
    expect(canvas()?.layout).toBe(layout);
  });

  it('says so, and draws nothing, when no machine governs the entity', async () => {
    await render({ governedMachine: undefined });

    expect(text()).toContain('No state machine governs this entity type.');
    expect(canvas()).toBeUndefined();
  });

  /** `GovernedStateConsistencyCheck` documents the case: the object exists but is outside its machine. */
  it('reports a record the operation layer could tell it nothing about', async () => {
    await render({ state: undefined });

    expect(text()).toContain('This record has no state yet.');
    // The machine is still drawn — a reader wants to see the lifecycle even when this record is outside it.
    expect(canvas()).toBeDefined();
  });

  it('warns when the record sits in a state the machine no longer declares', async () => {
    await render({ state: objectState('WITHDRAWN') });

    expect(text()).toContain("'WITHDRAWN' is not a state this machine declares.");
  });

  /** A machine that has never been arranged is the normal starting point; the canvas lays it out itself. */
  it('draws the machine without a layout when it has never been arranged', async () => {
    await render({ state: objectState('DRAFT'), layoutFails: true });

    expect(canvas()).toBeDefined();
    expect(canvas()?.layout).toBeUndefined();
    expect(text()).toContain('Draft');
  });

  /** Nothing identifies the entity without it, so there is no machine to look up. */
  it('shows the empty state when the outlet handed it no descriptor', async () => {
    await render({ descriptor: undefined, governedMachine: undefined });

    expect(text()).toContain('No state machine governs this entity type.');
  });

  it('says it is read-only, since a diagram that cannot be edited and does not say why reads as broken', async () => {
    await render();

    expect(text()).toContain('Read-only view.');
  });
});
