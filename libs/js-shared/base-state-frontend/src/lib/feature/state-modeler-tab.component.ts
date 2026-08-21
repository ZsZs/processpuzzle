import { Component, effect, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { StateMachineDefinitionStore } from '../domain/state-machine-definition.store';
import { STATE_MODELER_I18N_KEY } from '../base-state.i18n';
import { StateMachineCanvasComponent } from './modeler/components/state-machine-canvas.component';

/**
 * The State Modeler tab's screen, mounted at `state-machine-definition/<entityName>/modeler` by
 * {@link BASE_STATE_ROUTES} — a sibling of the generic Details form rather than something stacked under
 * it, which is what the `extraTabs` hook on `BaseEntityDescriptor` exists for.
 *
 * A placeholder for now: the tab, its route, its label and its tests land first, so the work that follows
 * is only the canvas. States and transitions stay authorable on the Details tab through the embedded
 * lists until then, which is what the message says.
 *
 * The icon is a `material-symbols-outlined` span rather than a `mat-icon`, the same choice
 * `UnderConstructionComponent` in `@processpuzzle/design` makes: this library declares no
 * `@angular/material` peer dependency, and a placeholder is not the reason to acquire one.
 */
@Component({
  selector: 'pp-state-modeler-tab',
  standalone: true,
  imports: [TranslocoPipe, StateMachineCanvasComponent],
  template: `
    <div class="pp-state-modeler">
      <h2 class="pp-state-modeler__title">{{ modelerLabelKey | transloco }}</h2>
      <span class="material-symbols-outlined pp-state-modeler__icon">construction</span>
      <p class="pp-state-modeler__message">{{ 'base_state.state_machine_definition.modeler.under_construction' | transloco }}</p>
      <pp-state-machine-canvas></pp-state-machine-canvas>
    </div>
  `,
  styles: [
    `
      /* The same white card surface as the status bar above it — same white, same corner radius. */
      .pp-state-modeler {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 8px;
        background-color: #ffffff;
        border-radius: 6px;
        padding: 16px 20px 24px;
        text-align: center;
      }
      .pp-state-modeler__title {
        margin: 0;
      }
      .pp-state-modeler__icon {
        font-size: 64px;
      }
      .pp-state-modeler__message {
        margin: 0;
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

  private readonly store = inject(StateMachineDefinitionStore);

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
      if (this.store.entities().length > 0) this.store.setCurrentEntity(this.entityId());
    });
  }
}
