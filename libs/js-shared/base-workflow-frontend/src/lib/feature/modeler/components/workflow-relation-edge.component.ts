import { Component, computed, input } from '@angular/core';
import { Edge, NgDiagramBaseEdgeComponent, NgDiagramBaseEdgeLabelComponent, NgDiagramDefaultEdgeLabelComponent, NgDiagramEdgeTemplate } from 'ng-diagram';
import { WorkflowEdgeData, WorkflowRelation } from '../../../domain/modeler/workflow-graph';

/**
 * How each relation of the Workflows perspective is drawn — registered against
 * `WORKFLOW_RELATION_EDGE_TYPE` in {@link WorkflowDiagramComponent}'s edge template map.
 *
 * The Roles perspective needs none of this: one relation on screen reads as itself, so its edges set no
 * `type` and keep ng-diagram's default template. This one has six, and a diagram where a dependency and a
 * tool call looked alike would be a diagram of nothing in particular.
 *
 * The distinction is BPMN's, because it is the one a reader of a process diagram already has: **control flow
 * is solid, association is dotted.** A sequence edge is what has to happen before what; a data or tool edge
 * says what a task touches, which does not order anything.
 *
 * Every relation is directed and so carries ng-diagram's `ng-diagram-arrow`, its only built-in marker. The
 * dash pattern rather than the arrowhead is what tells them apart, which is also why an `implicit` edge is
 * dashed *and* faded: it is a real ordering, but one inferred from the row order of a form rather than
 * stated, and it should not read as firmly as the dependency beside it.
 *
 * Drawing the label is not optional. ng-diagram's default template draws `data.label` and is not exported,
 * so a custom template that omitted it would silently lose every `ANY` join marker and every tool operation
 * name. The components underneath are the library's own, so the chip is its chip rather than a copy of it.
 */
@Component({
  selector: 'pp-workflow-relation-edge',
  standalone: true,
  imports: [NgDiagramBaseEdgeComponent, NgDiagramBaseEdgeLabelComponent, NgDiagramDefaultEdgeLabelComponent],
  template: `
    <ng-diagram-base-edge
      [edge]="edge()"
      targetArrowhead="ng-diagram-arrow"
      [strokeDasharray]="style().dash"
      [strokeOpacity]="style().opacity"
      [attr.data-relation]="relation()"
    >
      @if (label(); as text) {
        <ng-diagram-base-edge-label id="edge-label" [positionOnEdge]="0.5">
          <ng-diagram-default-edge-label>{{ text }}</ng-diagram-default-edge-label>
        </ng-diagram-base-edge-label>
      }
    </ng-diagram-base-edge>
  `,
  styles: `
    /* Hover and selection feedback, which the default edge template contributes through a class whose
       styles are scoped to it — so a custom template has to restate them. The values are ng-diagram's own
       tokens, not colours of ours: an edge should keep looking like an edge of this library. */
    ng-diagram-base-edge:hover:not(.selected):not(.temporary) {
      --edge-stroke: var(--ngd-default-edge-stroke-hover);
      --edge-label-border-color: var(--ngd-default-edge-stroke-hover);
    }
    ng-diagram-base-edge.selected {
      --edge-stroke: var(--ngd-default-edge-stroke-selected);
      --edge-label-border-color: var(--ngd-default-edge-stroke-selected);
    }
  `,
})
export class WorkflowRelationEdgeComponent implements NgDiagramEdgeTemplate<WorkflowEdgeData> {
  readonly edge = input.required<Edge<WorkflowEdgeData>>();

  /**
   * Optional-chained throughout, because `data` is absent on an edge ng-diagram's own linking created rather
   * than the converter. `validateConnection` refuses those, so one should not arise — but an edge template
   * is not where to find out.
   */
  protected readonly relation = computed(() => this.edge().data?.relation);
  protected readonly label = computed(() => this.edge().data?.label);
  protected readonly style = computed(() => RELATION_STYLES[this.relation() ?? 'sequence']);
}

/**
 * The dash pattern and weight per relation.
 *
 * `undefined` dash is a solid line — the value ng-diagram's own default edge uses, so a sequence edge is
 * drawn exactly as the library draws an edge.
 */
const RELATION_STYLES: Record<WorkflowRelation, { dash?: string; opacity: number }> = {
  // Control flow: what the model states outright.
  sequence: { dash: undefined, opacity: 1 },
  // Control flow the model only implies, through the order two rows were declared in.
  implicit: { dash: '6 5', opacity: 0.55 },
  // Associations. Dotted, as BPMN draws a data association.
  input: { dash: '2 4', opacity: 0.8 },
  output: { dash: '2 4', opacity: 0.8 },
  start: { dash: '2 4', opacity: 0.8 },
  tool: { dash: '2 4', opacity: 0.8 },
};
