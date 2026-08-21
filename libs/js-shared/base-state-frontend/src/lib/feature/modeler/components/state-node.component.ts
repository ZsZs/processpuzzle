import { Component, computed, input } from '@angular/core';
import { NgDiagramNodeSelectedDirective, NgDiagramNodeTemplate, NgDiagramPortComponent, Node } from 'ng-diagram';
import { StateNodeData } from '../../../domain/modeler/graph/state-machine-graph';

/**
 * How one state is drawn on the canvas — registered against `STATE_NODE_TYPE` in
 * {@link StateMachineCanvasComponent}'s node template map.
 *
 * A custom template rather than ng-diagram's default one for a specific reason: `terminal`, `locked` and
 * `initial` are the three things about a state that a reader of the diagram needs to see without clicking
 * anything, and the default template renders a label and nothing else.
 *
 * A port on each of the four sides, all `both`, because {@link EdgeLayout} persists the anchors a user
 * dragged an edge onto — a saved `port-top` has to have something to bind to on reload, or the edge
 * silently re-anchors and the diagram reopens with different geometry from the one that was saved.
 */
@Component({
  selector: 'pp-state-node',
  standalone: true,
  imports: [NgDiagramPortComponent],
  hostDirectives: [{ directive: NgDiagramNodeSelectedDirective, inputs: ['node'] }],
  host: { '[class.ng-diagram-port-hoverable-over-node]': 'true' },
  template: `
    <div class="state-node" [class.terminal]="state().terminal" [class.locked]="state().locked" [class.initial]="node().data.initial">
      <div class="title">{{ node().data.label }}</div>
      @if (state().description) {
        <div class="description">{{ state().description }}</div>
      }
    </div>

    <ng-diagram-port id="port-left" side="left" type="both" />
    <ng-diagram-port id="port-top" side="top" type="both" />
    <ng-diagram-port id="port-right" side="right" type="both" />
    <ng-diagram-port id="port-bottom" side="bottom" type="both" />
  `,
  styles: `
    .state-node {
      width: 150px;
      padding: 8px;
      border-radius: 6px;
      background: #fff;
      border: 1px solid #ccc;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
      cursor: pointer;
    }
    /* The state an object starts in — a thicker left border, reading as the entry point of a
       left-to-right layout. */
    .state-node.initial {
      border-left: 4px solid var(--pp-color-dark-blue, rgb(24, 111, 206));
    }
    .state-node.terminal {
      border-color: #d9534f;
    }
    .state-node.locked {
      opacity: 0.6;
    }
    .title {
      font-weight: 600;
    }
    .description {
      margin-top: 4px;
      font-size: 12px;
      color: #666;
    }
  `,
})
export class StateNodeComponent implements NgDiagramNodeTemplate<StateNodeData> {
  readonly node = input.required<Node<StateNodeData>>();

  /** The `State` behind this node, so the template reads flags off the domain object rather than copies. */
  protected readonly state = computed(() => this.node().data.state);
}
