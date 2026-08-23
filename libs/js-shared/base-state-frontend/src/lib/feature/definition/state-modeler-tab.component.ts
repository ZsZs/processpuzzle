import { Component, computed, effect, inject, input, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { STATE_MODELER_I18N_KEY } from '../../base-state.i18n';
import { DiagramDefinitionStore } from '../../domain/modeler/data-access/diagram-definition.store';
import { StateMachineDefinition } from '../../domain/definition/state-machine-definition';
import { StateMachineDefinitionStore } from '../../domain/definition/state-machine-definition.store';
import { StateMachineCanvasComponent } from '../modeler/components/state-machine-canvas.component';
import { StatePropertiesPanelComponent } from '../modeler/pages/state-properties-panel.component';
import { TransitionPropertiesPanelComponent } from '../modeler/pages/transition-properties-panel.component';
import { DiagramSelectionService } from '../modeler/services/diagram-selection.service';

/**
 * The State Modeler tab's screen, mounted at `state-machine-definition/<entityName>/modeler` by
 * {@link BASE_STATE_ROUTES} — a sibling of the generic Details form rather than something stacked under
 * it, which is what the `extraTabs` hook on `BaseEntityDescriptor` exists for.
 *
 * The host of the modeler, and the one place the machine's two halves are fetched: the topology from
 * `StateMachineDefinitionStore` and the arrangement from `DiagramDefinitionStore`. The canvas joins them
 * and gives both back to be saved; nothing else in the modeler talks to a store.
 *
 * Transitions are still authored on the Details tab: the transition properties panel shows the selected
 * one but does not edit it, and no gesture here draws a new one.
 */
@Component({
  selector: 'pp-state-modeler-tab',
  standalone: true,
  imports: [TranslocoPipe, StateMachineCanvasComponent, StatePropertiesPanelComponent, TransitionPropertiesPanelComponent],
  template: `
    <div class="pp-state-modeler">
      <div class="pp-state-modeler__toolbar">
        <button type="button" class="pp-state-modeler__save" [disabled]="!machine() || diagramStore.isLoading()" (click)="save()">
          {{ 'base_state.state_machine_definition.modeler.save' | transloco }}
        </button>
      </div>

      <div class="pp-state-modeler__body">
        <pp-state-machine-canvas class="pp-state-modeler__canvas" [machine]="machine()" [layout]="layout()" />

        <aside class="pp-state-modeler__properties">
          @if (selection.selectedState(); as state) {
            <pp-state-properties-panel
              [state]="state"
              [initial]="selection.selectedStateIsInitial()"
              [keyEditable]="selectedStateIsNew()"
              (stateChanged)="canvas()?.applyStateEdit($event)"
            />
          }
          @if (selection.selectedTransition(); as transition) {
            <pp-transition-properties-panel [transition]="transition" />
          }
        </aside>
      </div>
    </div>
  `,
  styles: [
    `
      /* The same white card surface as the status bar above it — same white, same corner radius. */
      .pp-state-modeler {
        display: flex;
        flex-direction: column;
        gap: 8px;
        background-color: #ffffff;
        border-radius: 6px;
        padding: 16px 20px 24px;
      }
      .pp-state-modeler__toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 16px;
      }
      .pp-state-modeler__title {
        margin: 0;
      }
      /* A plain button, not a mat-button: this library declares no @angular/material peer dependency, and
         one save action is not the reason to acquire one. */
      .pp-state-modeler__save {
        padding: 6px 16px;
        border: none;
        border-radius: 4px;
        background-color: var(--pp-button-primary-bg, rgb(24, 111, 206));
        color: var(--pp-button-primary-text, #eeeeee);
        cursor: pointer;
      }
      .pp-state-modeler__save:disabled {
        opacity: 0.5;
        cursor: default;
      }
      /* The canvas takes the room; the properties column is fixed, so a long description cannot squeeze
         the diagram. */
      .pp-state-modeler__body {
        display: grid;
        grid-template-columns: 1fr 260px;
        gap: 16px;
        min-height: 420px;
      }
      .pp-state-modeler__canvas {
        border: 1px solid #cccccc;
        border-radius: 4px;
      }
      .pp-state-modeler__properties {
        font-size: 14px;
      }
    `,
  ],
})
export class StateModelerTabComponent {
  /**
   * Bound from the route's `:entityId` param by `withComponentInputBinding()`, the same way
   * `BaseEntityFormComponent` receives it — so a deep link and a reload resolve the same definition as a
   * click through the tab does.
   */
  readonly entityId = input.required<string>();

  /** The tab's own label, reused as the screen's heading so the two cannot disagree. */
  protected readonly modelerLabelKey = STATE_MODELER_I18N_KEY;
  protected readonly selection = inject(DiagramSelectionService);
  protected readonly diagramStore = inject(DiagramDefinitionStore);

  private readonly machineStore = inject(StateMachineDefinitionStore);

  protected readonly canvas = viewChild(StateMachineCanvasComponent);
  protected readonly machine = this.machineStore.currentEntity;
  protected readonly layout = this.diagramStore.currentEntity;

  /**
   * Whether the selected state is one that has been drawn but never saved — which is the only kind whose
   * key may still be edited. The key is what every object of the entity type carries as its status, so
   * re-keying a state the machine has been running with would orphan the objects already sitting in it.
   *
   * Answered by asking the loaded machine, not the canvas: a state the machine declares is a state the
   * backend has already seen, whatever the canvas has done to it since.
   */
  protected readonly selectedStateIsNew = computed(() => {
    const selectedKey = this.selection.selectedState()?.key;
    return selectedKey !== undefined && !(this.machine()?.states ?? []).some((state) => state.key === selectedKey);
  });

  constructor() {
    // Selects the definition, so the tab bar's Details link stays enabled and the status bar keeps naming
    // the record — arriving here directly, nothing else has selected it.
    //
    // An effect rather than a call in `ngOnInit`, the same shape `BaseEntityFormComponent` uses:
    // `setCurrentEntity` resolves the id against the rows the store already holds and *clears* the
    // selection when it finds none, and the store loads asynchronously from its own `onInit`. On a deep
    // link or a reload the rows have not arrived by the time this component initializes, so a single
    // early call would do the opposite of what it is here for.
    effect(() => {
      if (this.machineStore.entities().length > 0) this.machineStore.setCurrentEntity(this.entityId());
    });

    // The arrangement is fetched by name rather than resolved out of the loaded list: the layout of one
    // machine is what this screen needs, and `loadLayout` reports "never arranged" as an absent layout
    // rather than as an error — which is the canvas's cue to fall back to an automatic layout.
    effect(() => {
      void this.diagramStore.loadLayout(this.entityId());
    });
  }

  /**
   * Persists what has been drawn: the arrangement and the topology, in that order. One gesture, because
   * the split into two resources is the backend's business and not a decision to hand the user — and
   * because `PUT /diagrams/{entityName}` is an upsert, so it makes no difference whether this machine has
   * been arranged before.
   *
   * Two rules keep the saves from undoing each other. **Both payloads are read first**, because each save
   * writes back into a store whose signal is one of the canvas's inputs, and the canvas rebuilds its model
   * when an input changes. And **the layout goes first**, because `saveDiagramDefinition` explicitly does
   * not validate `stateKey` — a row naming a state the machine does not declare yet is tolerated, so a
   * position may be persisted ahead of the state it belongs to. The reverse order would land the new state
   * while the old arrangement was still current, and `DagreLayoutService` would park the new node in a
   * layout row of its own choosing rather than where it was dropped.
   *
   * If the layout save fails the machine is left alone: an orphaned layout row is invisible and is pruned
   * by the next save, whereas a state with no position would be parked somewhere the user did not put it.
   */
  protected async save(): Promise<void> {
    const canvas = this.canvas();
    const layout = canvas?.toLayout();
    const machine = canvas?.toMachine();
    if (!layout || !machine) return;

    if (!(await this.diagramStore.saveLayout(layout))) return;
    // Derived from the loaded machine, which the store selected out of the rows it fetched — so it carries
    // the id `update` locks on. The cast states that, since the converter's return type cannot.
    await this.machineStore.update(machine as PersistedEntity<StateMachineDefinition>);
  }
}
