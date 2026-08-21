import { Component, Input } from '@angular/core';
import { GraphEdge } from '../../../domain/modeler/graph/graph-model/graph-edge';

@Component({
  selector: 'pp-transition-edge',
  standalone: true,
  template: `
    <svg class="transition-edge">
      <line [attr.x1]="edgePoints?.x1" [attr.y1]="edgePoints?.y1" [attr.x2]="edgePoints?.x2" [attr.y2]="edgePoints?.y2" stroke="#333" stroke-width="2" />

      <text [attr.x]="labelPoint?.x" [attr.y]="labelPoint?.y" class="edge-label">
        {{ edge?.label }}
      </text>
    </svg>
  `,
  styles: [
    `
      .edge-label {
        font-size: 12px;
        fill: #333;
      }
    `,
  ],
})
export class TransitionEdgeComponent {
  @Input() edge!: GraphEdge;
  @Input() edgePoints?: { x1: number; y1: number; x2: number; y2: number };
  @Input() labelPoint?: { x: number; y: number };
}
