import { Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { STATE_MACHINE_STATE_I18N_SCOPE } from '../../../base-state.i18n';
import { State } from '../../../domain/state-machine-definition';

/** One edit of the selected state, as {@link StateMachineCanvasComponent.applyStateEdit} applies it. */
export interface StateEdit {
  /**
   * The key the state had before the edit. Separate from `state.key` because the key is the node's
   * identity: only knowing both can the canvas tell a rename from any other change.
   */
  previousKey: string;
  state: State;
  /** Whether this is the state the machine starts in. */
  initial: boolean;
}

/**
 * The selected state's fields, editable.
 *
 * Edits are emitted rather than written: the canvas model is the authority on the machine while the
 * modeler is open — see {@link StateMachineCanvasComponent} — and a panel that mutated its input would put
 * a second one beside it.
 *
 * `(change)` rather than `(input)`, so an edit is emitted when a field is committed. The emitted state
 * comes back as this panel's input, and re-reading it on every keystroke would fight the caret.
 *
 * A **key** is only editable while the state is new. It is the identity a transition names and, more to
 * the point, the literal value written onto every object of the entity type — re-keying a state a machine
 * has been running with would silently orphan the objects already sitting in it. Renaming an existing
 * state stays a deliberate act on the Details tab.
 */
@Component({
  selector: 'pp-state-properties-panel',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <h3>{{ scope + '._self' | transloco }}</h3>

    <label>
      <span>{{ scope + '.key' | transloco }}</span>
      <input type="text" [value]="state().key" [readOnly]="!keyEditable()" [attr.data-testid]="'state-key'" (change)="editKey($event)" />
    </label>

    <label>
      <span>{{ scope + '.name' | transloco }}</span>
      <input type="text" [value]="state().name" [attr.data-testid]="'state-name'" (change)="editName($event)" />
    </label>

    <label>
      <span>{{ scope + '.description' | transloco }}</span>
      <textarea rows="2" [value]="state().description ?? ''" [attr.data-testid]="'state-description'" (change)="editDescription($event)"></textarea>
    </label>

    <label class="pp-state-properties__flag">
      <input type="checkbox" [checked]="initial()" [attr.data-testid]="'state-initial'" (change)="editInitial($event)" />
      <span>{{ scope + '.initial' | transloco }}</span>
    </label>

    <label class="pp-state-properties__flag">
      <input type="checkbox" [checked]="state().isFinal" [attr.data-testid]="'state-is-final'" (change)="editIsFinal($event)" />
      <span>{{ scope + '.isFinal' | transloco }}</span>
    </label>

    <label class="pp-state-properties__flag">
      <input type="checkbox" [checked]="state().isLocked" [attr.data-testid]="'state-is-locked'" (change)="editIsLocked($event)" />
      <span>{{ scope + '.isLocked' | transloco }}</span>
    </label>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    h3 {
      margin: 0;
    }
    label {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    label.pp-state-properties__flag {
      flex-direction: row;
      align-items: center;
      gap: 6px;
    }
    input[type='text'],
    textarea {
      font: inherit;
      padding: 4px 6px;
      border: 1px solid #cccccc;
      border-radius: 4px;
    }
    /* A key that cannot be changed still has to be readable — greyed rather than hidden. */
    input[readonly] {
      background-color: #f2f2f2;
      color: #666666;
    }
  `,
})
export class StatePropertiesPanelComponent {
  readonly state = input.required<State>();
  /** Whether this is the state the machine starts in — held by the machine, not by the state itself. */
  readonly initial = input(false);
  /** True only for a state the loaded machine does not declare yet. See the class comment. */
  readonly keyEditable = input(false);

  readonly stateChanged = output<StateEdit>();

  protected readonly scope = STATE_MACHINE_STATE_I18N_SCOPE;

  protected editKey(event: Event): void {
    // An emptied key would make the state unidentifiable, so the previous one stands.
    const key = value(event).trim();
    if (key) this.emit({ key });
  }

  protected editName(event: Event): void {
    this.emit({ name: value(event) });
  }

  protected editDescription(event: Event): void {
    this.emit({ description: value(event) || undefined });
  }

  protected editIsFinal(event: Event): void {
    this.emit({ isFinal: checked(event) });
  }

  protected editIsLocked(event: Event): void {
    this.emit({ isLocked: checked(event) });
  }

  /**
   * Unticking is ignored: a machine has to start somewhere, and the state that takes over is chosen by
   * ticking *it*. The tick is therefore a one-way claim, which is also what keeps the canvas's
   * "clear the flag on the others" rule the only way the flag is ever cleared.
   */
  protected editInitial(event: Event): void {
    if (checked(event)) this.emit({}, true);
  }

  private emit(patch: Partial<State>, initial = this.initial()): void {
    const current = this.state();
    this.stateChanged.emit({ previousKey: current.key, state: new State({ ...current, ...patch }), initial });
  }
}

// region private helper functions
function value(event: Event): string {
  return (event.target as HTMLInputElement | HTMLTextAreaElement).value;
}

function checked(event: Event): boolean {
  return (event.target as HTMLInputElement).checked;
}
// endregion
