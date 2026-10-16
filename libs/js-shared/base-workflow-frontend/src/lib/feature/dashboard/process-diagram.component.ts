import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { WORKFLOW_I18N_SCOPE } from '../../base-workflow.i18n';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';
import { ArtifactDefinitionStore } from '../../domain/definition/artifact-definition.store';
import { RoleDefinitionStore } from '../../domain/definition/role-definition.store';
import { TaskDefinitionStore } from '../../domain/definition/task-definition.store';
import { ToolDefinitionStore } from '../../domain/definition/tool-definition.store';
import { WorkflowStore } from '../../domain/definition/workflow.store';
import { WorkflowDiagramService } from '../../domain/modeler/data-access/workflow-diagram.service';
import { SwimlaneLayoutService } from '../../domain/modeler/graph/swimlane-layout.service';
import { WorkflowFlowGraphConverter } from '../../domain/modeler/graph/workflow-flow-graph.converter';
import { WorkflowDiagram } from '../../domain/modeler/models/workflow-diagram';
import { elementNodeId, WorkflowGraph } from '../../domain/modeler/workflow-graph';
import { WorkflowDiagramComponent } from '../modeler/components/workflow-diagram.component';
import { WorkflowSelectionService } from '../modeler/services/workflow-selection.service';

/** What a layout lookup answered: the arrangement, or `undefined` for a workflow never arranged. */
type LayoutLookup = { readonly layout: WorkflowDiagram | undefined };

/**
 * The open run drawn as its workflow: the modeler's swimlane diagram, read-only, with the run's state on it.
 *
 * **The modeler's diagram, not a second one.** The same converter, the same layout service and the same
 * canvas the Workflow Modeler tab draws with, and the arrangement the user saved there — so the flow looks
 * here exactly as its author left it. That is what the process board's own comment asked for in place of a
 * graph view of its own: live status layered onto the modeler's existing nodes. Three things are layered:
 *
 * - each task and event node carries its instance's status (`runStatus`), which the node template rings —
 *   active work and a waiting catch loudest, a blocked task in red, finished work faded;
 * - the open task is `highlighted`, the mark the modeler uses for "the element you came from";
 * - each artifact is labelled with the record the run actually holds — `ORD-0042 : Order` — rather than the
 *   workflow's object name, once the run has bound it (`ArtifactInstance.entityLabel`).
 *
 * **Read-only**, which is `WorkflowDiagramComponent`'s default: nothing drags, and there is no Save. Clicking a
 * task opens it in the pane below, as a click on its board card does.
 *
 * The layout is fetched through `WorkflowDiagramService` directly rather than through the root
 * `WorkflowDiagramStore`, whose current entity is the modeler's: loading another workflow's layout into it from
 * here would be one screen changing what another one is editing. Its own `WorkflowSelectionService` for the
 * same reason — the root one is what the modeler's properties panel reads.
 */
@Component({
  selector: 'pp-process-diagram',
  standalone: true,
  imports: [WorkflowDiagramComponent],
  providers: [WorkflowSelectionService],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (graph().nodes.length > 0 && layoutLookup(); as lookup) {
      <pp-workflow-diagram class="process-diagram" [graph]="graph()" [layout]="swimlanes" [savedLayout]="lookup.layout" data-testid="process-diagram" />
    }
  `,
  styles: `
    /* A stated height, for the reason the modeler states one: ng-diagram renders blank in a box without one. */
    .process-diagram {
      display: block;
      height: 420px;
      border: 1px solid #dddddd;
      border-radius: 4px;
    }
  `,
})
export class ProcessDiagramComponent {
  protected readonly store = inject(WorkflowDashboardStore);
  protected readonly swimlanes = inject(SwimlaneLayoutService);

  private readonly selection = inject(WorkflowSelectionService);
  private readonly layouts = inject(WorkflowDiagramService);
  private readonly transloco = inject(TranslocoService);
  private readonly workflowStore = inject(WorkflowStore);
  private readonly taskStore = inject(TaskDefinitionStore);
  private readonly roleStore = inject(RoleDefinitionStore);
  private readonly artifactStore = inject(ArtifactDefinitionStore);
  private readonly toolStore = inject(ToolDefinitionStore);

  /** Workflow id → its arrangement, once looked up. Kept, so switching back to a run's workflow costs nothing. */
  private readonly lookups = signal<ReadonlyMap<string, LayoutLookup>>(new Map());

  private readonly workflowId = computed(() => this.store.selectedInstance()?.workflowId);

  /**
   * The open run's layout lookup, or `undefined` while it is pending. The canvas waits for it, as the
   * modeler's does: it frames its model when the model is created, and one created on the automatic layout
   * would have to re-frame when the saved viewport arrived.
   */
  protected readonly layoutLookup = computed(() => {
    const workflowId = this.workflowId();
    return workflowId ? this.lookups().get(workflowId) : undefined;
  });

  /** The workflow, with the open run's status, selection and records laid over it. */
  protected readonly graph = computed<WorkflowGraph>(() => {
    const instance = this.store.selectedInstance();
    const workflow = instance ? this.workflowStore.entities().find((candidate) => candidate.id === instance.workflowId) : undefined;
    if (!instance || !workflow || this.taskStore.entities().length === 0) return { nodes: [], edges: [] };

    const graph = WorkflowFlowGraphConverter.toGraph(workflow, this.taskStore.entities(), this.roleStore.entities(), this.artifactStore.entities(), this.toolStore.entities(), {
      objectNames: this.store.selectedObjectNames(),
      labels: {
        unassignedLane: this.transloco.translate(`${WORKFLOW_I18N_SCOPE}.modeler.unassigned`),
        anyJoin: this.transloco.translate(`${WORKFLOW_I18N_SCOPE}.modeler.join_any`),
        endEvent: this.transloco.translate(`${WORKFLOW_I18N_SCOPE}.modeler.end`),
      },
    });

    const statuses = new Map<string, string | undefined>([
      ...(instance.tasks ?? []).map((task) => [elementNodeId('task', task.taskDefinitionId), task.status] as const),
      ...(instance.events ?? []).map((event) => [elementNodeId('event', event.eventUseId), event.status] as const),
    ]);
    const openTask = this.store.selectedTask();
    const openNodeId = openTask ? elementNodeId('task', openTask.taskDefinitionId) : undefined;
    return {
      ...graph,
      nodes: graph.nodes.map((node) => {
        const runStatus = statuses.get(node.id);
        const highlighted = node.id === openNodeId;
        return runStatus || highlighted ? { ...node, data: { ...node.data, ...(runStatus ? { runStatus } : {}), highlighted } } : node;
      }),
    };
  });

  constructor() {
    effect(() => {
      const workflowId = this.workflowId();
      if (workflowId && !untracked(this.lookups).has(workflowId)) void this.lookUp(workflowId);
    });

    // A click on a task opens that task of this run below — the diagram as a way in, like the board.
    effect(() => {
      const element = this.selection.selectedElement();
      const instance = untracked(this.store.selectedInstance);
      if (!instance || element?.kind !== 'task' || !element.elementId) return;
      const task = (instance.tasks ?? []).find((candidate) => candidate.taskDefinitionId === element.elementId);
      if (task && task.id !== untracked(this.store.selectedTaskId)) untracked(() => this.store.select(instance.id, task.id));
    });
  }

  /** A failed lookup draws the automatic layout rather than nothing: the arrangement is a nicety, the flow is not. */
  private async lookUp(workflowId: string): Promise<void> {
    let layout: WorkflowDiagram | undefined;
    try {
      layout = await firstValueFrom(this.layouts.findByWorkflowId(workflowId));
    } catch {
      layout = undefined;
    }
    this.lookups.update((lookups) => new Map(lookups).set(workflowId, { layout }));
  }
}
