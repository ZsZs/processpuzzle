import { Component, Input } from '@angular/core';
import { GraphNode } from '../../../domain/modeler/graph/graph-model/graph-node';

@Component({
  selector: 'pp-state-node',
  standalone: true,
  template: `
    <div class="state-node" [class.terminal]="node.data.terminal" [class.locked]="node.data.locked">
      <div class="title">{{ node.label }}</div>
      <div class="description">{{ node.data.description }}</div>
    </div>
  `,
  styles: [
    `
      .state-node {
        width: 150px;
        padding: 8px;
        border-radius: 6px;
        background: #fff;
        border: 1px solid #ccc;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        cursor: pointer;
      }
      .state-node.terminal {
        border-color: #d9534f;
      }
      .state-node.locked {
        opacity: 0.6;
      }
      .title {
        font-weight: 600;
        margin-bottom: 4px;
      }
      .description {
        font-size: 12px;
        color: #666;
      }
    `,
  ],
})
export class StateNodeComponent {
  @Input() node!: GraphNode;
}
