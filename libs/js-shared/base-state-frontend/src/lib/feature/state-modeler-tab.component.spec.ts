import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { OTHER_STATE_MACHINE_DEFINITION_DTO, pageOfStateMachineDefinitions, STATE_MACHINE_DEFINITION_DTO } from '../domain/test-state-machine-definition';
import { StateMachineDefinitionStore } from '../domain/state-machine-definition.store';
import { StateModelerTabComponent } from './state-modeler-tab.component';

const SERVICE_ROOT = 'http://localhost:3000/organizations/processpuzzle-testbed';

describe('StateModelerTabComponent', () => {
  let fixture: ComponentFixture<StateModelerTabComponent>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // Flat dotted keys: TestTranslocoLoader drops nested objects when no scope is configured.
        provideTranslocoTesting({
          translations: {
            en: {
              'base_state.state_machine_definition.tabs.modeler': 'State Modeler',
              'base_state.state_machine_definition.modeler.under_construction': 'The state machine modeler is under construction.',
            },
          },
        }),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { STATE_SERVICE_ROOT: SERVICE_ROOT } } },
      ],
    });
    fixture = TestBed.createComponent(StateModelerTabComponent);
    controller = TestBed.inject(HttpTestingController);
  });

  async function render(entityId = 'order'): Promise<void> {
    fixture.componentRef.setInput('entityId', entityId);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  /** The store's own `onInit` hook issues this; the rows it brings are what `setCurrentEntity` resolves against. */
  function flushMachines(): void {
    controller.expectOne(`${SERVICE_ROOT}/state-machines`).flush(pageOfStateMachineDefinitions(STATE_MACHINE_DEFINITION_DTO, OTHER_STATE_MACHINE_DEFINITION_DTO));
  }

  it('says what the screen is and that it is not built yet', async () => {
    await render();
    flushMachines();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('State Modeler');
    expect(text).toContain('under construction');
  });

  /**
   * Not decoration: arriving here by deep link or reload nothing else has selected the definition, and
   * with no current entity the tab bar disables the Details link and the status bar stops naming the row.
   */
  it('selects the definition the route addresses, so the tab bar and the status bar keep working', async () => {
    await render('dynamic-entity');
    flushMachines();
    await fixture.whenStable();

    expect(TestBed.inject(StateMachineDefinitionStore).currentId()).toBe('dynamic-entity');
  });

  /**
   * The rows arrive after this component initializes on a deep link, which is why the selection is an
   * effect rather than a one-shot call in `ngOnInit`: `setCurrentEntity` clears the selection when it
   * resolves against an empty store, so an early call would do the opposite of what it is here for.
   */
  it('waits for the rows to arrive rather than clearing the selection before they do', async () => {
    await render();

    expect(TestBed.inject(StateMachineDefinitionStore).currentId()).toBeUndefined();

    flushMachines();
    await fixture.whenStable();

    expect(TestBed.inject(StateMachineDefinitionStore).currentId()).toBe('order');
  });
});
