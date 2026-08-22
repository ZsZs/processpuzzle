import { Component, computed, input } from '@angular/core';
import { NgDiagramNodeSelectedDirective, NgDiagramNodeTemplate, NgDiagramPortComponent, Node } from 'ng-diagram';
import { StateNodeData } from '../../../domain/modeler/graph/state-machine-graph';
import { stateKind } from './state-palette-items';

/**
 * How one state is drawn on the canvas — registered against `STATE_NODE_TYPE` in
 * {@link StateMachineCanvasComponent}'s node template map.
 *
 * A custom template rather than ng-diagram's default one because a state's *role* in the machine is the
 * first thing a reader of the diagram has to see, and the default template renders a label and nothing
 * else. The three shapes are UML's, so a state machine drawn here reads as a state machine drawn anywhere:
 *
 * - the state an object starts in — a small filled disc, UML's initial pseudostate;
 * - a state it ends in — a larger ringed disc, UML's final state;
 * - every other state — a labelled rounded box.
 *
 * Which of the three a state is comes from {@link stateKind}, the same rule the palette's symbols are
 * built by, so what is dragged in and what is drawn afterwards cannot disagree.
 *
 * The two discs carry their name *beside* the shape rather than inside it, out of flow: a circle with text
 * in it is no longer a circle, and ng-diagram measures the node by this template's box — which is the box
 * edges anchor to and `DiagramDefinition` records. Keeping the label out of that box is what lets a disc
 * stay round at any name length.
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
    <div class="state-node" [class]="'state-node--' + kind()" [class.locked]="state().locked" [attr.data-testid]="'state-node-' + kind()">
      @if (kind() === 'state') {
        <div class="title">{{ node().data.label }}</div>
        @if (state().description) {
          <div class="description">{{ state().description }}</div>
        }
      } @else {
        <div class="disc-label" [attr.title]="state().description ?? null">{{ node().data.label }}</div>
      }
    </div>

    <ng-diagram-port id="port-left" side="left" type="both" />
    <ng-diagram-port id="port-top" side="top" type="both" />
    <ng-diagram-port id="port-right" side="right" type="both" />
    <ng-diagram-port id="port-bottom" side="bottom" type="both" />
  `,
  styles: `
    .state-node {
      box-sizing: border-box;
      cursor: pointer;
    }
    /* An ordinary state: the box its name and description are read from. */
    .state-node--state {
      width: 150px;
      padding: 8px;
      border-radius: 6px;
      background: #fff;
      border: 1px solid #ccc;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    }
    /* The two discs share their geometry and differ only in fill, which is the whole point of the UML
       pair: one solid mark for the entry, the same mark ringed for the exit. Positioned, because that is what
       the ring and the label are placed against. */
    .state-node--start,
    .state-node--end {
      position: relative;
      border-radius: 50%;
      background: #fff;
    }
    .state-node--start {
      width: 24px;
      height: 24px;
      background: var(--pp-color-dark-blue, rgb(24, 111, 206));
    }
    .state-node--end {
      width: 34px;
      height: 34px;
      border: 2px solid #d9534f;
    }
    /* The inner disc of the double circle. A pseudo-element rather than a nested div, so the shape is one
       element and nothing in the template has to know it is drawn from two. */
    .state-node--end::after {
      content: '';
      position: absolute;
      inset: 4px;
      border-radius: 50%;
      background: #d9534f;
    }
    /* Out of flow, so it neither stretches the disc nor enlarges the box ng-diagram measures. Centred
       under the shape and never wrapped: a name is read at a glance here, and the properties panel is
       where it is read in full. */
    .disc-label {
      position: absolute;
      top: calc(100% + 4px);
      left: 50%;
      transform: translateX(-50%);
      white-space: nowrap;
      font-size: 12px;
      font-weight: 600;
      pointer-events: none;
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

  /** Which of the three UML shapes this state is drawn as. */
  protected readonly kind = computed(() => stateKind(this.state(), this.node().data.initial));
}
