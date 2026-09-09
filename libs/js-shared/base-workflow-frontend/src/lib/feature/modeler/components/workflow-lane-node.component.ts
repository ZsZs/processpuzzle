import { Component, computed, input } from '@angular/core';
import { NgDiagramGroupHighlightedDirective, NgDiagramGroupNodeTemplate } from 'ng-diagram';
import { modelerIconUrl } from '../../../domain/modeler/modeler-icons';
import { WorkflowLaneNode, WorkflowNodeData } from '../../../domain/modeler/workflow-graph';

/**
 * One swimlane — registered against `WORKFLOW_LANE_TYPE` in {@link WorkflowDiagramComponent}'s node
 * template map, beside the element template rather than instead of it.
 *
 * A band as wide as the whole diagram, with a header column down its left edge naming the role whose tasks
 * it holds. The tasks are **not** rendered here: they are ng-diagram nodes of their own whose `groupId`
 * names this lane, drawn by the element template and positioned by `SwimlaneLayoutService` to land inside
 * this box. So this template draws only the box and the header, and must leave its body empty and
 * transparent — anything painted there would sit under its own tasks.
 *
 * Three things it deliberately does not do.
 *
 * **It states no size.** The band's width and height are the layout's, written onto the node together with
 * `autoSize: false` — which is load-bearing rather than tidy, because ng-diagram defaults `autoSize` to true
 * and, when true, discards an explicit size in favour of its own default. A lane is the one node in this
 * modeler whose box is computed rather than measured, since it has to contain nodes it does not render.
 *
 * **It draws no description.** A role's description belongs on a role *card*, which the Roles perspective
 * draws; a lane header a hundred pixels tall would take the room its tasks need.
 *
 * **It offers no gesture.** Lanes come through the canvas's `lock()` like every other node, so there is
 * nothing to drag, resize or rotate. `ngDiagramGroupHighlighted` is still applied — it is how ng-diagram
 * says "a node is being dragged over this lane", which cannot happen here, but a group template that
 * silently dropped the library's own affordance would be the wrong thing to copy for a lane one day.
 */
@Component({
  selector: 'pp-workflow-lane-node',
  standalone: true,
  imports: [NgDiagramGroupHighlightedDirective],
  template: `
    <div class="lane" ngDiagramGroupHighlighted [node]="node()" [attr.data-testid]="'workflow-lane'" [attr.data-unresolved]="unresolved() || null">
      <div class="lane__header">
        <img class="lane__symbol" [src]="iconUrl" alt="" aria-hidden="true" />
        <span class="lane__label">{{ data().label }}</span>
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
      height: 100%;
    }
    /* The band. A tinted wash rather than a solid fill: the tasks sit on top of it, and an opaque band
       would hide the edges that cross between lanes when ng-diagram elevates a selected lane. */
    .lane {
      display: flex;
      box-sizing: border-box;
      width: 100%;
      height: 100%;
      border: 1px solid #cccccc;
      border-radius: 4px;
      background-color: rgb(from var(--pp-surface-card, rgb(153, 217, 235)) r g b / 12%);
    }
    /* The header column. Its width is what SwimlaneLayoutService reserves before the first task column, so
       the two have to agree — see HEADER_WIDTH there. */
    .lane__header {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      box-sizing: border-box;
      width: 140px;
      flex: 0 0 140px;
      padding: 8px;
      border-right: 1px solid #cccccc;
      background-color: rgb(from var(--pp-surface-card, rgb(153, 217, 235)) r g b / 28%);
    }
    .lane__symbol {
      width: 24px;
      height: 28px;
      object-fit: contain;
    }
    /* Centred rather than rotated, which is what a BPMN tool does with a tall pool header. A band here is
       one or two rows deep, so upright text fits and stays selectable and translatable. */
    .lane__label {
      font-size: 13px;
      font-weight: 600;
      text-align: center;
      overflow-wrap: anywhere;
    }
    /* A role the catalog does not contain, or none at all — the same dashed-red vocabulary the element
       template uses for a reference that resolves to nothing. */
    .lane[data-unresolved] .lane__header {
      border-right-style: dashed;
      border-right-color: #c62828;
    }
    .lane[data-unresolved] .lane__label {
      font-family: monospace;
      font-weight: 400;
      color: #c62828;
    }
  `,
})
export class WorkflowLaneNodeComponent implements NgDiagramGroupNodeTemplate<WorkflowNodeData> {
  readonly node = input.required<WorkflowLaneNode>();

  protected readonly data = computed(() => this.node().data);
  protected readonly unresolved = computed(() => this.data().unresolved === true);

  /** Always the role symbol: a lane is a role, whatever else it may hold. */
  protected readonly iconUrl = modelerIconUrl('role');
}
