import { Component, Input } from '@angular/core';
import { StateDefinition } from '../../domain/models/state-definition';

@Component({
  selector: 'pp-state-properties-panel',
  standalone: true,
  template: `
    <h3>State</h3>
    <div><strong>Key:</strong> {{ state.key }}</div>
    <div><strong>Name:</strong> {{ state.name }}</div>
    <div><strong>Description:</strong> {{ state.description }}</div>
    <div><strong>Locked:</strong> {{ state.locked }}</div>
    <div><strong>Terminal:</strong> {{ state.terminal }}</div>
  `,
})
export class StatePropertiesPanelComponent {
  @Input() state!: StateDefinition;
}
