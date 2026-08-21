import { Component, Input } from '@angular/core';
import { TransitionDefinition } from '../../domain/models/transition-definition';

@Component({
  selector: 'pp-transition-properties-panel',
  standalone: true,
  template: `
    <h3>Transition</h3>
    <div><strong>Key:</strong> {{ transition.key }}</div>
    <div><strong>Name:</strong> {{ transition.name }}</div>
    <div><strong>Source:</strong> {{ transition.sourceStateKey }}</div>
    <div><strong>Target:</strong> {{ transition.targetStateKey }}</div>
    <div><strong>Trigger:</strong> {{ transition.triggerKey }}</div>
  `,
})
export class TransitionPropertiesPanelComponent {
  @Input() transition!: TransitionDefinition;
}
