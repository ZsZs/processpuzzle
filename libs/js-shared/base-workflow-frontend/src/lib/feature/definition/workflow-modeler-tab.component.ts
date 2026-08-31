import { Component, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TranslocoService } from '@jsverse/transloco';
import { WORKFLOW_I18N_SCOPE } from '../../base-workflow.i18n';
import { ArtifactDefinitionStore } from '../../domain/definition/artifact-definition.store';
import { RoleDefinitionStore } from '../../domain/definition/role-definition.store';
import { TaskDefinitionStore } from '../../domain/definition/task-definition.store';
import { ToolDefinitionStore } from '../../domain/definition/tool-definition.store';
import { WorkflowStore } from '../../domain/definition/workflow.store';
import { WorkflowDiagramStore } from '../../domain/modeler/data-access/workflow-diagram.store';
import { SwimlaneLayoutService } from '../../domain/modeler/graph/swimlane-layout.service';
import { WorkflowFlowGraphConverter } from '../../domain/modeler/graph/workflow-flow-graph.converter';
import { WorkflowElementKind } from '../../domain/modeler/workflow-graph';
import { ModelerLayers, ModelerLayerTogglesComponent } from '../modeler/components/modeler-layer-toggles.component';
import { ModelerLegendComponent } from '../modeler/components/modeler-legend.component';
import { WorkflowDiagramComponent } from '../modeler/components/workflow-diagram.component';
import { WorkflowElementPropertiesPanelComponent } from '../modeler/pages/workflow-element-properties-panel.component';
import { WorkflowRelationPropertiesPanelComponent } from '../modeler/pages/workflow-relation-properties-panel.component';
import { WorkflowSelectionService } from '../modeler/services/workflow-selection.service';

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
 * The host of the Workflows perspective and the one place its data is fetched. **Six stores**, because a
 * workflow holds ids, a diagram needs names, and an arrangement is a resource of its own: the workflow
 * itself, the task catalog for each task's name, inputs, outputs and steps, the role, artifact and tool
 * catalogs for the rest, and `WorkflowDiagramStore` for where the user last put everything. Injecting a
 * catalog store *is* the request for its contents — each one's root-scoped `onInit` issues the load — so this
 * constructor is what makes the requests that nothing else on this route branch would make.
 *
 * Arrangement and composition stay apart, exactly as the backend keeps them: this screen saves the
 * *arrangement* and nothing else. What the workflow contains is authored on the Details tab, and the four
 * catalogs on their own screens — which is why the properties panel here shows rather than edits.
 */
@Component({
  selector: 'pp-workflow-modeler-tab',
  standalone: true,
  imports: [
    TranslocoPipe,
    ModelerLegendComponent,
    ModelerLayerTogglesComponent,
    WorkflowDiagramComponent,
    WorkflowElementPropertiesPanelComponent,
    WorkflowRelationPropertiesPanelComponent,
  ],
  template: `
    <div class="pp-workflow-modeler">
      <div class="pp-workflow-modeler__toolbar">
        <pp-modeler-legend [kinds]="drawnKinds" />
        <pp-modeler-layer-toggles [layers]="layers()" [labelScope]="modelerScope" (layersChange)="layers.set($event)" />
        <button type="button" class="pp-workflow-modeler__save" [disabled]="!canSave()" data-testid="workflow-modeler-save" (click)="save()">
          {{ saveKey | transloco }}
        </button>
      </div>

      @if (diagramStore.error(); as error) {
        <p class="pp-workflow-modeler__error" role="alert" data-testid="workflow-modeler-error">{{ error }}</p>
      }

      <div class="pp-workflow-modeler__body">
        <!-- Rendered only once there is something to draw *and* the saved arrangement has been looked up:
             zoomToFit frames the diagram when its model is created, so a canvas created empty and filled
             when the catalogs arrive would frame nothing — and one created before the layout lookup settles
             would fit the automatic layout and then have to re-frame to the saved viewport. -->
        @if (hasGraph() && layoutSettled()) {
          <pp-workflow-diagram
            class="pp-workflow-modeler__canvas"
            [graph]="graph()"
            [layout]="swimlanes"
            [savedLayout]="savedLayout()"
            [editable]="true"
            data-testid="workflow-modeler-diagram"
          />
        } @else {
          <p class="pp-workflow-modeler__empty" data-testid="workflow-modeler-empty">{{ emptyKey | transloco }}</p>
        }

        <aside class="pp-workflow-modeler__properties">
          @if (selection.selectedElement(); as element) {
            <pp-workflow-element-properties-panel [element]="element" [isLane]="selection.selectedElementIsLane()" />
          } @else if (selection.selectedRelation(); as relation) {
            <pp-workflow-relation-properties-panel [relation]="relation" />
          } @else {
            <p class="pp-workflow-modeler__no-selection" data-testid="workflow-modeler-no-selection">{{ noSelectionKey | transloco }}</p>
          }
        </aside>
      </div>
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
    /* Legend left, toggles and Save right. Wrapping rather than shrinking, because the toggles are labelled
       words in five languages and a German one is not the width of an English one. */
    .pp-workflow-modeler__toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px 16px;
    }
    /* A plain button, not a mat-button: this library declares no @angular/material peer dependency, and one
       save action is not the reason to acquire one. Same shape as the State Modeler's own Save. */
    .pp-workflow-modeler__save {
      padding: 6px 16px;
      border: none;
      border-radius: 4px;
      background-color: var(--pp-button-primary-bg, rgb(24, 111, 206));
      color: var(--pp-button-primary-text, #eeeeee);
      cursor: pointer;
    }
    .pp-workflow-modeler__save:disabled {
      opacity: 0.5;
      cursor: default;
    }
    .pp-workflow-modeler__error {
      margin: 0;
      color: #b00020;
      font-size: 13px;
    }
    /* The canvas takes the room; the properties column is fixed, so a long description cannot squeeze the
       diagram. */
    .pp-workflow-modeler__body {
      display: grid;
      grid-template-columns: 1fr 280px;
      gap: 16px;
    }
    /* A stated height, not a proportion of the viewport: ng-diagram measures the box it is given and renders
       blank — with no error of any kind — when that box has no definite height. Taller than the Roles canvas
       because a swimlane diagram is bands stacked vertically, so its natural aspect is deeper. */
    .pp-workflow-modeler__canvas {
      height: 600px;
      border: 1px solid #cccccc;
      border-radius: 4px;
    }
    .pp-workflow-modeler__properties {
      font-size: 14px;
    }
    .pp-workflow-modeler__empty,
    .pp-workflow-modeler__no-selection {
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
  protected readonly saveKey = `${WORKFLOW_I18N_SCOPE}.modeler.save`;
  protected readonly noSelectionKey = `${WORKFLOW_I18N_SCOPE}.modeler.properties.none`;
  protected readonly swimlanes = inject(SwimlaneLayoutService);
  protected readonly selection = inject(WorkflowSelectionService);
  protected readonly diagramStore = inject(WorkflowDiagramStore);

  private readonly workflowStore = inject(WorkflowStore);
  private readonly taskStore = inject(TaskDefinitionStore);
  private readonly roleStore = inject(RoleDefinitionStore);
  private readonly artifactStore = inject(ArtifactDefinitionStore);
  private readonly toolStore = inject(ToolDefinitionStore);
  private readonly transloco = inject(TranslocoService);

  protected readonly canvas = viewChild(WorkflowDiagramComponent);

  /**
   * Whether the arrangement lookup for *this* workflow has come back — either with a layout or with the 404
   * that means "never arranged". Tracked here rather than read off `isLoading`, which is also false before
   * the request starts; the canvas waits on it so that it is created once, already knowing whether there is
   * a viewport to restore.
   */
  private readonly layoutSettledSignal = signal(false);
  protected readonly layoutSettled = this.layoutSettledSignal.asReadonly();

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
   * The saved arrangement of the workflow on screen, or `undefined` when it has never been arranged.
   *
   * Guarded by `workflowId`, not just taken from the store: `WorkflowDiagramStore` is root-scoped, so
   * navigating from one workflow's modeler to another's leaves the previous layout current until the new
   * lookup resolves — and applying it would move this workflow's tasks to another one's positions.
   */
  protected readonly savedLayout = computed(() => {
    const layout = this.diagramStore.currentEntity();
    return layout?.workflowId === this.entityId() ? layout : undefined;
  });

  /**
   * Whether there is a diagram to draw.
   *
   * Asked of the graph's nodes rather than of one store, because this perspective needs **two** loads to say
   * anything: the workflow, for its tasks, and the task catalog, for their names. A workflow that has arrived
   * with an empty task catalog would otherwise draw a diagram of unresolved boxes and then re-frame a moment
   * later when the names landed.
   */
  protected readonly hasGraph = computed(() => this.graph().nodes.length > 0 && this.taskStore.entities().length > 0);

  /** Nothing to save until there is a diagram on screen, and not while a save or a load is in flight. */
  protected readonly canSave = computed(() => this.hasGraph() && this.layoutSettled() && !this.diagramStore.isLoading());

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

    // The arrangement is fetched by id rather than resolved out of the loaded list: the layout of one
    // workflow is what this screen needs, and `loadLayout` reports "never arranged" as an absent layout
    // rather than as an error — which is the canvas's cue to keep the automatic swimlane layout.
    effect(() => {
      const workflowId = this.entityId();
      this.layoutSettledSignal.set(false);
      void this.diagramStore.loadLayout(workflowId).finally(() => this.layoutSettledSignal.set(true));
    });
  }

  /**
   * Persists what the user arranged, and nothing else.
   *
   * One resource, unlike the State Modeler's Save, which writes the arrangement *and* the topology: there,
   * the canvas is where states are added and edited, so a save has two halves to keep in step. Here the
   * canvas cannot change what the workflow contains — see `WorkflowDiagramComponent`'s class comment — so
   * the only thing there is to write is the layout.
   *
   * Failures surface through the store's `error`, which the toolbar shows; there is nothing to roll back,
   * since a rejected save leaves both the canvas and the server exactly as they were.
   */
  protected async save(): Promise<void> {
    const layout = this.canvas()?.toLayout(this.entityId());
    if (!layout) return;
    await this.diagramStore.saveLayout(layout);
  }
}
