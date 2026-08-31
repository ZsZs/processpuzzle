import { Component, computed, effect, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { WORKFLOW_ROLE_DEFINITION_I18N_SCOPE } from '../../base-workflow.i18n';
import { ArtifactDefinitionStore } from '../../domain/definition/artifact-definition.store';
import { RoleDefinitionStore } from '../../domain/definition/role-definition.store';
import { RoleResponsibilityGraphConverter } from '../../domain/modeler/graph/role-responsibility-graph.converter';
import { WorkflowElementKind } from '../../domain/modeler/workflow-graph';
import { ModelerLegendComponent } from '../modeler/components/modeler-legend.component';
import { WorkflowDiagramComponent } from '../modeler/components/workflow-diagram.component';

/** The kinds this perspective draws, and the order the legend reads them in. */
const DRAWN_KINDS: WorkflowElementKind[] = ['role', 'artifact'];

/**
 * The Role Modeler tab's screen: every role in the organisation and the artifacts each one is responsible
 * for, mounted at `workflow-role-definition/:entityId/modeler` — a sibling of the generic Details form
 * rather than something stacked under it, which is what `extraTabs` on `BaseEntityDescriptor` exists for.
 *
 * The tab mounts per row, but the diagram is of the whole organisation: responsibility is only legible
 * against what everyone *else* owns, and one role with its artifacts is what the Details form already
 * shows. The role the tab was opened from is marked instead of filtered on.
 *
 * The host of the Roles perspective and the one place its data is fetched. Both catalogs come from their
 * stock stores, whose root-scoped `onInit` issues the load — so injecting `ArtifactDefinitionStore` *is*
 * the request for the artifact catalog, which nothing else on this route branch would otherwise fetch.
 */
@Component({
  selector: 'pp-role-modeler-tab',
  standalone: true,
  imports: [TranslocoPipe, ModelerLegendComponent, WorkflowDiagramComponent],
  template: `
    <div class="pp-role-modeler">
      <div class="pp-role-modeler__toolbar">
        <pp-modeler-legend [kinds]="drawnKinds" />
      </div>

      <!-- Rendered only once there is something to draw: zoomToFit.onInit frames the diagram when it
           initializes, so a canvas created empty and filled when the catalogs arrive would frame nothing. -->
      @if (hasGraph()) {
        <pp-workflow-diagram class="pp-role-modeler__canvas" [graph]="graph()" data-testid="role-modeler-diagram" />
      } @else {
        <p class="pp-role-modeler__empty" data-testid="role-modeler-empty">{{ emptyKey | transloco }}</p>
      }
    </div>
  `,
  styles: `
    /* The same white card surface as the status bar above it — same white, same corner radius. */
    .pp-role-modeler {
      display: flex;
      flex-direction: column;
      gap: 8px;
      background-color: #ffffff;
      border-radius: 6px;
      padding: 16px 20px 24px;
    }
    .pp-role-modeler__toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
    }
    /* A stated height, not a proportion of the viewport: ng-diagram measures the box it is given and
       renders blank — with no error of any kind — when that box has no definite height. */
    .pp-role-modeler__canvas {
      height: 520px;
      border: 1px solid #cccccc;
      border-radius: 4px;
    }
    .pp-role-modeler__empty {
      margin: 24px 0;
      color: #666666;
    }
  `,
})
export class RoleModelerTabComponent {
  /**
   * Bound from the route's `:entityId` param by `withComponentInputBinding()`, the same way
   * `BaseEntityFormComponent` receives it — so a deep link and a reload mark the same role as a click
   * through the tab does.
   */
  readonly entityId = input.required<string>();

  protected readonly drawnKinds = DRAWN_KINDS;
  protected readonly emptyKey = `${WORKFLOW_ROLE_DEFINITION_I18N_SCOPE}.modeler.empty`;

  private readonly roleStore = inject(RoleDefinitionStore);
  private readonly artifactStore = inject(ArtifactDefinitionStore);

  /**
   * The perspective, rebuilt whenever either catalog changes or a different role is opened. A `computed`
   * rather than an effect writing a signal: the graph is a projection of the two lists and the marked id,
   * with no state of its own to keep in step.
   */
  protected readonly graph = computed(() =>
    RoleResponsibilityGraphConverter.toGraph(this.roleStore.entities(), this.artifactStore.entities(), this.entityId()),
  );

  /**
   * Whether there is a diagram to draw. Asked of the roles rather than of the graph's nodes, so that "the
   * catalog is still loading" and "this organisation has authored no roles" both read as nothing to draw —
   * the empty message says the latter, which is the only one a settled screen can be in.
   */
  protected readonly hasGraph = computed(() => this.roleStore.entities().length > 0);

  constructor() {
    // Selects the role, so the tab bar's Details link stays enabled and the status bar keeps naming the
    // record — arriving here directly, nothing else has selected it.
    //
    // An effect rather than a call in `ngOnInit`, the same shape `BaseEntityFormComponent` uses:
    // `setCurrentEntity` resolves the id against the rows the store already holds and *clears* the
    // selection when it finds none, and the store loads asynchronously from its own `onInit`. On a deep
    // link or a reload the rows have not arrived by the time this component initializes, so a single early
    // call would do the opposite of what it is here for.
    effect(() => {
      if (this.roleStore.entities().length > 0) this.roleStore.setCurrentEntity(this.entityId());
    });
  }
}
