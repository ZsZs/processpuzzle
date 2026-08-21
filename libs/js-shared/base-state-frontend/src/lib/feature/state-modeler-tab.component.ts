import { Component, effect, inject, input, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { STATE_MODELER_I18N_KEY } from '../base-state.i18n';
import { DiagramDefinitionStore } from '../domain/modeler/data-access/diagram-definition.store';
import { StateMachineDefinitionStore } from '../domain/state-machine-definition.store';
import { StateMachineCanvasComponent } from './modeler/components/state-machine-canvas.component';
import { StatePropertiesPanelComponent } from './modeler/pages/state-properties-panel.component';
import { TransitionPropertiesPanelComponent } from './modeler/pages/transition-properties-panel.component';
import { DiagramSelectionService } from './modeler/services/diagram-selection.service';

/**
 * The State Modeler tab's screen, mounted at `state-machine-definition/<entityName>/modeler` by
 * {@link BASE_STATE_ROUTES} — a sibling of the generic Details form rather than something stacked under
 * it, which is what the `extraTabs` hook on `BaseEntityDescriptor` exists for.
 *
 * The host of the modeler, and the one place the machine's two halves are fetched: the topology from
 * `StateMachineDefinitionStore` and the arrangement from `DiagramDefinitionStore`. The canvas joins them
 * and gives back an arrangement to save; nothing else in the modeler talks to a store.
 *
 * Still read-only in one respect: the properties panels show the selected state or transition but do not
 * edit it. Authoring states and transitions stays on the Details tab through its embedded lists until a
 * write path back into `StateMachineDefinition` lands, which the Add State / Add Transition gestures need.
 */
@Component({
  selector: 'pp-state-modeler-tab',
  standalone: true,
  imports: [TranslocoPipe, StateMachineCanvasComponent, StatePropertiesPanelComponent, TransitionPropertiesPanelComponent],
  template: `
    <div class="pp-state-modeler">
      <div class="pp-state-modeler__toolbar">
        <button type="button" class="pp-state-modeler__save" [disabled]="!machine() || diagramStore.isLoading()" (click)="saveLayout()">
          {{ 'base_state.state_machine_definition.modeler.save_layout' | transloco }}
        </button>
      </div>

      <div class="pp-state-modeler__body">
        <pp-state-machine-canvas class="pp-state-modeler__canvas" [machine]="machine()" [layout]="layout()" />

        <aside class="pp-state-modeler__properties">
          @if (selection.selectedState(); as state) {
            <pp-state-properties-panel [state]="state" />
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
  private readonly canvas = viewChild(StateMachineCanvasComponent);

  protected readonly machine = this.machineStore.currentEntity;
  protected readonly layout = this.diagramStore.currentEntity;

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
   * Persists the arrangement as it now stands. One gesture, because `PUT /diagrams/{entityName}` is an
   * upsert — there is no create-or-replace decision for the user to make, whether or not this machine has
   * been arranged before.
   */
  protected async saveLayout(): Promise<void> {
    const layout = this.canvas()?.toLayout();
    if (layout) await this.diagramStore.saveLayout(layout);
  }
}
