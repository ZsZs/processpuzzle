import { Component, computed, inject, input } from '@angular/core';
import { Edge, NgDiagramBaseEdgeComponent, NgDiagramBaseEdgeLabelComponent, NgDiagramDefaultEdgeLabelComponent, NgDiagramEdgeTemplate } from 'ng-diagram';
import { TransitionEdgeData } from '../../../domain/modeler/graph/state-machine-graph';
import { EdgeContextMenuService } from '../services/edge-context-menu.service';

/**
 * How one transition is drawn — registered against `TRANSITION_EDGE_TYPE` in
 * {@link StateMachineCanvasComponent}'s edge template map.
 *
 * Three things, and the first is the reason the other two are here at all.
 *
 * **A right-click that names this edge.** ng-diagram's own edge renders an SVG path with nowhere to
 * listen, and a `contextmenu` handler on the canvas would know the pointer landed on an edge but not on
 * which one — whereas an edge template is handed its `edge`, so the question does not arise. That is what
 * {@link EdgeRoutingMenuComponent} needs and what no configuration of the default edge could provide.
 *
 * **The label.** ng-diagram's default edge template draws `data.label` and is not exported, so a custom
 * template has to draw it too or the labels simply vanish. Same components underneath — a base edge label
 * at the midpoint wrapping `ng-diagram-default-edge-label` — so it is the library's own chip, not a copy
 * of its look.
 *
 * **The arrowhead.** A transition runs from one state to another and reads as such only if it is drawn
 * with a direction; ng-diagram's built-in `ng-diagram-arrow` marker is what draws it. Set here rather
 * than on the edges the converter builds, because it is true of every transition without exception —
 * putting it in the model would mean a field written on every load and never read back.
 *
 * The routing is left to `edge.routing`, which the base edge reads for itself: that is the field the
 * routing menu writes and `EdgeLayout` persists.
 */
@Component({
  selector: 'pp-transition-edge',
  standalone: true,
  imports: [NgDiagramBaseEdgeComponent, NgDiagramBaseEdgeLabelComponent, NgDiagramDefaultEdgeLabelComponent],
  template: `
    <ng-diagram-base-edge [edge]="edge()" targetArrowhead="ng-diagram-arrow" (contextmenu)="onContextMenu($event)">
      @if (label(); as text) {
        <ng-diagram-base-edge-label id="edge-label" [positionOnEdge]="0.5">
          <ng-diagram-default-edge-label>{{ text }}</ng-diagram-default-edge-label>
        </ng-diagram-base-edge-label>
      }
    </ng-diagram-base-edge>
  `,
  styles: `
    /* Hover and selection feedback, which the default edge template contributes through a class of its own
       whose styles are scoped to it — so a custom template has to restate them. The values are ng-diagram's
       own tokens, not colours of ours: an edge should keep looking like an edge of this library. Everything
       else the base edge already defaults to the same tokens. */
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
export class TransitionEdgeComponent implements NgDiagramEdgeTemplate<TransitionEdgeData> {
  readonly edge = input.required<Edge<TransitionEdgeData>>();

  private readonly contextMenu = inject(EdgeContextMenuService);

  /**
   * What is written on the edge. Optional-chained because `data` is absent on an edge ng-diagram's linking
   * created rather than the converter — refused by `validateConnection`, so one should not arise, but a
   * label is not the place to find out.
   */
  protected readonly label = computed(() => this.edge().data?.label);

  protected onContextMenu(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.contextMenu.open(this.edge().id, event);
  }
}
