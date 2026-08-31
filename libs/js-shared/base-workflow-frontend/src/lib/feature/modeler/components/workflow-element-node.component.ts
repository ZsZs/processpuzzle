import { Component, computed, input } from '@angular/core';
import { NgDiagramNodeSelectedDirective, NgDiagramNodeTemplate, NgDiagramPortComponent, Node } from 'ng-diagram';
import { modelerIconUrl } from '../../../domain/modeler/modeler-icons';
import { WorkflowNodeData } from '../../../domain/modeler/workflow-graph';

/**
 * How one element is drawn on a modeler canvas — registered against `WORKFLOW_NODE_TYPE` in
 * {@link WorkflowDiagramComponent}'s node template map.
 *
 * One template for all five kinds. Everything base-workflow models is *a thing with a name*, so what
 * distinguishes a role from an artifact on screen is its symbol and nothing else — the labelled card around
 * it is the same shape either way. Which symbol comes from {@link WorkflowNodeData.kind} through
 * {@link modelerIconUrl}, so adding the Task and Workflow perspectives adds no template.
 *
 * This is where it departs from base-state's `StateNodeComponent`, which draws three genuinely different
 * UML shapes and therefore branches in its template: a state machine's notation distinguishes an entry
 * point from a state, whereas SPEM's elements are distinguished by their icon.
 *
 * The symbol is an `<img>` rather than a `<mat-icon>` with a registered SVG: this library declares no
 * `@angular/material` peer dependency, as base-state's modeler does not, and one image per node is not the
 * reason to acquire one. The five files differ in aspect ratio — `Role.svg` is portrait, `Tool.svg` wide —
 * so the box is fixed and the image is contained inside it rather than sized by its own dimensions.
 *
 * A port on each of the four sides, all `both`, so an edge can anchor sensibly whichever way the layout put
 * the two nodes. Nothing persists an anchor, since these diagrams save no geometry; the ports are what let
 * ng-diagram pick one.
 */
@Component({
  selector: 'pp-workflow-element-node',
  standalone: true,
  imports: [NgDiagramPortComponent],
  hostDirectives: [{ directive: NgDiagramNodeSelectedDirective, inputs: ['node'] }],
  host: { '[class.ng-diagram-port-hoverable-over-node]': 'true' },
  template: `
    <div
      class="element"
      [class]="'element--' + data().kind"
      [class.element--highlighted]="highlighted()"
      [class.element--unresolved]="unresolved()"
      [attr.data-testid]="'workflow-node-' + data().kind"
      [attr.data-highlighted]="highlighted() ? 'true' : null"
      [attr.data-unresolved]="unresolved() ? 'true' : null"
    >
      <img class="element__symbol" [src]="iconUrl()" alt="" aria-hidden="true" />
      <div class="element__text">
        <div class="element__label">{{ data().label }}</div>
        @if (data().description) {
          <div class="element__description">{{ data().description }}</div>
        }
      </div>
    </div>

    <ng-diagram-port id="port-left" side="left" type="both" />
    <ng-diagram-port id="port-top" side="top" type="both" />
    <ng-diagram-port id="port-right" side="right" type="both" />
    <ng-diagram-port id="port-bottom" side="bottom" type="both" />
  `,
  styles: `
    /* A fixed width, so the estimate WorkflowLayoutService lays the graph out against is only ever wrong
       about the height of a description. */
    .element {
      box-sizing: border-box;
      display: flex;
      align-items: center;
      gap: 8px;
      width: 170px;
      padding: 8px;
      border-radius: 6px;
      background: #ffffff;
      border: 1px solid #cccccc;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
      cursor: pointer;
    }
    /* Contained rather than sized by the file: the five symbols have five different aspect ratios, and a
       row of nodes whose icons were each as wide as their artwork would not read as a row. */
    .element__symbol {
      flex: none;
      width: 32px;
      height: 36px;
      object-fit: contain;
    }
    /* Zero min-width, because a flex item's automatic minimum would otherwise let a long word widen the
       card past the width the layout was computed from. */
    .element__text {
      min-width: 0;
    }
    .element__label {
      font-weight: 600;
      font-size: 13px;
    }
    /* Two lines at most: a description is read in full on the element's own Details form. */
    .element__description {
      margin-top: 2px;
      font-size: 11px;
      color: #666666;
      display: -webkit-box;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 2;
      overflow: hidden;
    }
    /* The element the diagram was opened from. A ring drawn *outside* the card with box-shadow rather than
       a thicker border, because a border would change the box ng-diagram measured and shift every edge
       anchored to this node — the mark has to be free of the layout. */
    .element--highlighted {
      box-shadow:
        0 0 0 3px var(--pp-color-light-green, rgb(92, 218, 207)),
        0 0 0 8px rgba(92, 218, 207, 0.3),
        0 2px 8px rgba(0, 0, 0, 0.2);
    }
    /* A reference to something the catalog does not contain. The border *style* changes and its width does
       not, so the card measures the same as any other and the layout is unaffected. */
    .element--unresolved {
      border-style: dashed;
      border-color: #d9534f;
      background: #fdf7f7;
    }
    .element--unresolved .element__symbol {
      opacity: 0.5;
    }
    .element--unresolved .element__label {
      color: #d9534f;
      font-family: monospace;
    }
  `,
})
export class WorkflowElementNodeComponent implements NgDiagramNodeTemplate<WorkflowNodeData> {
  readonly node = input.required<Node<WorkflowNodeData>>();

  /** What this node carries, read once per change rather than through `node().data` in six bindings. */
  protected readonly data = computed(() => this.node().data);

  protected readonly iconUrl = computed(() => modelerIconUrl(this.data().kind));

  /** Both flags are optional in the data, and a template reads better against a definite boolean. */
  protected readonly highlighted = computed(() => this.data().highlighted === true);
  protected readonly unresolved = computed(() => this.data().unresolved === true);
}
