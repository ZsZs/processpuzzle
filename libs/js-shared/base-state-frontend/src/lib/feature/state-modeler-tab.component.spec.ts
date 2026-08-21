import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { DiagramDefinitionStore } from '../domain/modeler/data-access/diagram-definition.store';
import { DIAGRAM_DEFINITION_DTO, pageOfDiagramDefinitions } from '../domain/modeler/models/test-diagram-definition';
import { StateMachineDefinition } from '../domain/state-machine-definition';
import { StateMachineDefinitionStore } from '../domain/state-machine-definition.store';
import { OTHER_STATE_MACHINE_DEFINITION_DTO, pageOfStateMachineDefinitions, STATE_MACHINE_DEFINITION_DTO } from '../domain/test-state-machine-definition';
import { DiagramSelectionService } from './modeler/services/diagram-selection.service';
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
              'base_state.state_machine_definition.modeler.save_layout': 'Save layout',
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

  /** `DiagramDefinitionStore`'s own `onInit`, the counterpart of {@link flushMachines}. */
  function flushDiagramList(): void {
    controller.expectOne(`${SERVICE_ROOT}/diagrams`).flush(pageOfDiagramDefinitions(DIAGRAM_DEFINITION_DTO));
  }

  /** The tab's own read of one machine's arrangement, by entity name. */
  function flushLayout(entityId = 'order', body: object = DIAGRAM_DEFINITION_DTO): void {
    controller.expectOne(`${SERVICE_ROOT}/diagrams/${entityId}`).flush(body);
  }

  /** The never-arranged case: `GET /diagrams/{entityName}` answers 404, which is not an error condition. */
  function flushMissingLayout(entityId = 'order'): void {
    controller.expectOne(`${SERVICE_ROOT}/diagrams/${entityId}`).flush(null, { status: 404, statusText: 'Not Found' });
  }

  function saveButton(): HTMLButtonElement {
    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.pp-state-modeler__save');
    if (!button) throw new Error('The Save layout button is not rendered.');
    return button;
  }

  function currentMachine(): StateMachineDefinition {
    const machine = TestBed.inject(StateMachineDefinitionStore).currentEntity();
    if (!machine) throw new Error('No machine is selected.');
    return machine;
  }

  it('names the screen and offers the one gesture that persists an arrangement', async () => {
    await render();
    flushMachines();
    flushDiagramList();
    flushLayout();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('State Modeler');
    expect(text).toContain('Save layout');
  });

  /**
   * Not decoration: arriving here by deep link or reload nothing else has selected the definition, and
   * with no current entity the tab bar disables the Details link and the status bar stops naming the row.
   */
  it('selects the definition the route addresses, so the tab bar and the status bar keep working', async () => {
    await render('dynamic-entity');
    flushMachines();
    flushDiagramList();
    flushMissingLayout('dynamic-entity');
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
    flushDiagramList();
    flushLayout();
    await fixture.whenStable();

    expect(TestBed.inject(StateMachineDefinitionStore).currentId()).toBe('order');
  });

  // Addressed by name rather than resolved out of the loaded list: this screen needs one machine's
  // arrangement, and may well be the first thing the user opens.
  it('reads the arrangement of the machine the route addresses', async () => {
    await render();
    flushMachines();
    flushDiagramList();
    flushLayout();
    await fixture.whenStable();

    expect(TestBed.inject(DiagramDefinitionStore).currentEntity()?.nodes).toHaveLength(2);
  });

  // The 404 path. A machine with no arrangement is the normal starting point, so this must not surface as
  // an error, and the canvas has to render anyway from an automatic layout.
  it('renders a never-arranged machine without reporting an error', async () => {
    await render();
    flushMachines();
    flushDiagramList();
    flushMissingLayout();
    await fixture.whenStable();

    const diagramStore = TestBed.inject(DiagramDefinitionStore);
    expect(diagramStore.currentEntity()).toBeUndefined();
    expect(diagramStore.error()).toBeUndefined();
  });

  it('persists the arrangement the canvas holds through the upsert', async () => {
    await render();
    flushMachines();
    flushDiagramList();
    flushLayout();
    await fixture.whenStable();

    saveButton().click();

    const request = controller.expectOne(`${SERVICE_ROOT}/diagrams/order`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.nodes.map((node: { stateKey: string }) => node.stateKey)).toEqual(['DRAFT', 'DELIVERED']);
    // The version read on open, so a concurrent edit is still detectable.
    expect(request.request.body.version).toBe(3);
    request.flush({ ...DIAGRAM_DEFINITION_DTO, version: 4 });
  });

  it('offers nothing to save until a machine has loaded', async () => {
    await render();

    expect(saveButton().disabled).toBe(true);

    flushMachines();
    flushDiagramList();
    flushLayout();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(saveButton().disabled).toBe(false);
  });

  it('shows the properties of the state selected on the canvas', async () => {
    await render();
    flushMachines();
    flushDiagramList();
    flushLayout();
    await fixture.whenStable();

    TestBed.inject(DiagramSelectionService).selectState(currentMachine().states[0]);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('DRAFT');
    expect(text).toContain('Entered but not reviewed.');
  });

  it('shows the properties of the transition selected on the canvas', async () => {
    await render();
    flushMachines();
    flushDiagramList();
    flushLayout();
    await fixture.whenStable();

    TestBed.inject(DiagramSelectionService).selectTransition(currentMachine().transitions[0]);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Transition');
    expect(text).toContain('confirm');
  });
});
