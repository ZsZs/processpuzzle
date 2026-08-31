import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TranslocoService } from '@jsverse/transloco';
import { WORKFLOW_I18N_SCOPE } from '../../base-workflow.i18n';
import { ArtifactDefinitionStore } from '../../domain/definition/artifact-definition.store';
import { RoleDefinitionStore } from '../../domain/definition/role-definition.store';
import { TaskDefinitionStore } from '../../domain/definition/task-definition.store';
import { ToolDefinitionStore } from '../../domain/definition/tool-definition.store';
import { WorkflowStore } from '../../domain/definition/workflow.store';
import { SwimlaneLayoutService } from '../../domain/modeler/graph/swimlane-layout.service';
import { WorkflowFlowGraphConverter } from '../../domain/modeler/graph/workflow-flow-graph.converter';
import { WorkflowElementKind } from '../../domain/modeler/workflow-graph';
import { ModelerLayers, ModelerLayerTogglesComponent } from '../modeler/components/modeler-layer-toggles.component';
import { ModelerLegendComponent } from '../modeler/components/modeler-legend.component';
import { WorkflowDiagramComponent } from '../modeler/components/workflow-diagram.component';

/** The kinds this perspective draws, and the order the legend reads them in. */
const DRAWN_KINDS: WorkflowElementKind[] = ['role', 'task', 'artifact', 'tool'];

/** Everything on, which is the whole workflow — a toggle exists to take something away. */
const ALL_LAYERS: ModelerLayers = { lanes: true, data: true, tools: true };

/**
 * The Workflow Modeler tab's screen: one workflow's task flow as BPMN-style swimlanes, mounted at
 * `workflow/:entityId/modeler` — a sibling of the generic Details form rather than something stacked under
 * it, which is what `extraTabs` on `BaseEntityDescriptor` exists for.
 *
 * Unlike the Roles perspective, this one is *of one row*: a workflow is a self-contained composition, and
 * the four catalogs around it are only there to give its references names. So the `:entityId` is a filter
 * here rather than a mark.
 *
 * The host of the Workflows perspective and the one place its data is fetched. **Five stores**, because a
 * workflow holds ids and a diagram needs names: the workflow itself, the task catalog for each task's name,
 * inputs, outputs and steps, and the role, artifact and tool catalogs for the rest. Injecting a store *is*
 * the request for its catalog — each one's root-scoped `onInit` issues the load — so this constructor is
 * what makes five requests that nothing else on this route branch would make.
 */
@Component({
  selector: 'pp-workflow-modeler-tab',
  standalone: true,
  imports: [TranslocoPipe, ModelerLegendComponent, ModelerLayerTogglesComponent, WorkflowDiagramComponent],
  template: `
    <div class="pp-workflow-modeler">
      <div class="pp-workflow-modeler__toolbar">
        <pp-modeler-legend [kinds]="drawnKinds" />
        <pp-modeler-layer-toggles [layers]="layers()" [labelScope]="modelerScope" (layersChange)="layers.set($event)" />
      </div>

      <!-- Rendered only once there is something to draw: zoomToFit frames the diagram when its model is
           created, so a canvas created empty and filled when the catalogs arrive would frame nothing. -->
      @if (hasGraph()) {
        <pp-workflow-diagram class="pp-workflow-modeler__canvas" [graph]="graph()" [layout]="swimlanes" data-testid="workflow-modeler-diagram" />
      } @else {
        <p class="pp-workflow-modeler__empty" data-testid="workflow-modeler-empty">{{ emptyKey | transloco }}</p>
      }
    </div>
  `,
  styles: `
    /* The same white card surface as the status bar above it — same white, same corner radius. */
    .pp-workflow-modeler {
      display: flex;
      flex-direction: column;
      gap: 8px;
      background-color: #ffffff;
      border-radius: 6px;
      padding: 16px 20px 24px;
    }
    /* Legend left, toggles right. Wrapping rather than shrinking, because the toggles are labelled words in
       five languages and a German one is not the width of an English one. */
    .pp-workflow-modeler__toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px 16px;
    }
    /* A stated height, not a proportion of the viewport: ng-diagram measures the box it is given and
       renders blank — with no error of any kind — when that box has no definite height. Taller than the
       Roles canvas because a swimlane diagram is bands stacked vertically, so its natural aspect is deeper. */
    .pp-workflow-modeler__canvas {
      height: 600px;
      border: 1px solid #cccccc;
      border-radius: 4px;
    }
    .pp-workflow-modeler__empty {
      margin: 24px 0;
      color: #666666;
    }
  `,
})
export class WorkflowModelerTabComponent {
  /**
   * Bound from the route's `:entityId` param by `withComponentInputBinding()`, the same way
   * `BaseEntityFormComponent` receives it — so a deep link and a reload draw the same workflow as a click
   * through the tab does.
   */
  readonly entityId = input.required<string>();

  /** Which layers are on. A signal the toggles write and the converter reads. */
  protected readonly layers = signal<ModelerLayers>(ALL_LAYERS);

  protected readonly drawnKinds = DRAWN_KINDS;
  protected readonly modelerScope = `${WORKFLOW_I18N_SCOPE}.modeler`;
  protected readonly emptyKey = `${WORKFLOW_I18N_SCOPE}.modeler.empty`;
  protected readonly swimlanes = inject(SwimlaneLayoutService);

  private readonly workflowStore = inject(WorkflowStore);
  private readonly taskStore = inject(TaskDefinitionStore);
  private readonly roleStore = inject(RoleDefinitionStore);
  private readonly artifactStore = inject(ArtifactDefinitionStore);
  private readonly toolStore = inject(ToolDefinitionStore);
  private readonly transloco = inject(TranslocoService);

  /**
   * The perspective, rebuilt whenever the workflow, any catalog or any toggle changes. A `computed` rather
   * than an effect writing a signal: the graph is a projection of five lists and three flags, with no state
   * of its own to keep in step.
   *
   * The two labels are resolved here because the converter holds no transloco — the lane a task with no
   * stated performer goes in, and the word marking an `ANY` join, are the only two things it draws that are
   * not data.
   */
  protected readonly graph = computed(() =>
    WorkflowFlowGraphConverter.toGraph(this.workflowStore.currentEntity(), this.taskStore.entities(), this.roleStore.entities(), this.artifactStore.entities(), this.toolStore.entities(), {
      ...this.layers(),
      labels: {
        unassignedLane: this.transloco.translate(`${WORKFLOW_I18N_SCOPE}.modeler.unassigned`),
        anyJoin: this.transloco.translate(`${WORKFLOW_I18N_SCOPE}.modeler.join_any`),
      },
    }),
  );

  /**
   * Whether there is a diagram to draw.
   *
   * Asked of the graph's nodes rather than of one store, because this perspective needs **two** loads to say
   * anything: the workflow, for its tasks, and the task catalog, for their names. A workflow that has arrived
   * with an empty task catalog would otherwise draw a diagram of unresolved boxes and then re-frame a moment
   * later when the names landed.
   */
  protected readonly hasGraph = computed(() => this.graph().nodes.length > 0 && this.taskStore.entities().length > 0);

  constructor() {
    // Selects the workflow, which is both what this screen draws and what keeps the tab bar's Details link
    // enabled and the status bar naming the record.
    //
    // An effect rather than a call in `ngOnInit`, the same shape `BaseEntityFormComponent` uses:
    // `setCurrentEntity` resolves the id against the rows the store already holds and *clears* the selection
    // when it finds none, and the store loads asynchronously from its own `onInit`. On a deep link or a
    // reload the rows have not arrived by the time this component initializes, so a single early call would
    // do the opposite of what it is here for.
    effect(() => {
      if (this.workflowStore.entities().length > 0) this.workflowStore.setCurrentEntity(this.entityId());
    });
  }
}
