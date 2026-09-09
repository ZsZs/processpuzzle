import { Injectable, signal } from '@angular/core';
import { WorkflowEdgeData, WorkflowNodeData } from '../../../domain/modeler/workflow-graph';

/**
 * What the user has selected on a modeler canvas, as the two `data` shapes the nodes and edges carry.
 *
 * The `data` rather than the domain object behind it, which is where this departs from base-state's
 * `DiagramSelectionService`: that modeler *edits* the state behind a node, so its panel needs the whole
 * `State`. These panels only show what a node stands for, and `WorkflowNodeData` is already exactly that —
 * kind, label, description, and whether the reference resolved. Reaching back into four catalogs to
 * re-find the aggregate would make the panel a second reader of data the converter has already read.
 *
 * Signals rather than plain fields, because the selection is set from ng-diagram's `selectionChanged`
 * output — outside the panels' own change detection — and a plain field would leave a zoneless component
 * showing a stale subject.
 *
 * A selection is one or the other, never both: the two panels are alternatives, so selecting an edge has to
 * clear the node or both would render at once.
 */
@Injectable({ providedIn: 'root' })
export class WorkflowSelectionService {
  private readonly selectedElementSignal = signal<WorkflowNodeData | undefined>(undefined);
  private readonly selectedElementIsLaneSignal = signal(false);
  private readonly selectedRelationSignal = signal<WorkflowEdgeData | undefined>(undefined);

  readonly selectedElement = this.selectedElementSignal.asReadonly();
  /**
   * Whether the selected node is a **lane** rather than an element card. Carried alongside the data rather
   * than derived from it, because a lane's `data` is a role's `data` — `kind: 'role'` either way — so
   * nothing in it distinguishes the band from a card. The panel says which, since "Role" alone would be
   * ambiguous on a diagram that can hold both.
   */
  readonly selectedElementIsLane = this.selectedElementIsLaneSignal.asReadonly();
  readonly selectedRelation = this.selectedRelationSignal.asReadonly();

  selectElement(element: WorkflowNodeData, isLane = false): void {
    this.selectedElementSignal.set(element);
    this.selectedElementIsLaneSignal.set(isLane);
    this.selectedRelationSignal.set(undefined);
  }

  selectRelation(relation: WorkflowEdgeData): void {
    this.selectedRelationSignal.set(relation);
    this.selectedElementSignal.set(undefined);
    this.selectedElementIsLaneSignal.set(false);
  }

  clear(): void {
    this.selectedElementSignal.set(undefined);
    this.selectedElementIsLaneSignal.set(false);
    this.selectedRelationSignal.set(undefined);
  }
}
