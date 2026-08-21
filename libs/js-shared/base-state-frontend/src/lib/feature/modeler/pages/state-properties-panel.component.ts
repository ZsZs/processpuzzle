import { Component, input } from '@angular/core';
import { State } from '../../../domain/state-machine-definition';

/** Read-only for now: the state's fields as the canvas selection reports them. Editing lands with the Add
 * State gesture, which needs a write path back into `StateMachineDefinition`. */
@Component({
  selector: 'pp-state-properties-panel',
  standalone: true,
  template: `
    <h3>State</h3>
    <div><strong>Key:</strong> {{ state().key }}</div>
    <div><strong>Name:</strong> {{ state().name }}</div>
    <div><strong>Description:</strong> {{ state().description }}</div>
    <div><strong>Locked:</strong> {{ state().locked }}</div>
    <div><strong>Terminal:</strong> {{ state().terminal }}</div>
  `,
})
export class StatePropertiesPanelComponent {
  readonly state = input.required<State>();
}
