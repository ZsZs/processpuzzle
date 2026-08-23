import { Component, input } from '@angular/core';
import { Transition } from '../../../domain/definition/state-machine-definition';

/** Read-only, for the same reason as {@link StatePropertiesPanelComponent}. */
@Component({
  selector: 'pp-transition-properties-panel',
  standalone: true,
  template: `
    <h3>Transition</h3>
    <div><strong>Key:</strong> {{ transition().key }}</div>
    <div><strong>Name:</strong> {{ transition().name }}</div>
    <div><strong>Source:</strong> {{ transition().sourceStateKey }}</div>
    <div><strong>Target:</strong> {{ transition().targetStateKey }}</div>
    <div><strong>Trigger:</strong> {{ transition().triggerKey }}</div>
  `,
})
export class TransitionPropertiesPanelComponent {
  readonly transition = input.required<Transition>();
}
