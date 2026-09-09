import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TASK_DASHBOARD_I18N_SCOPE } from '../../base-workflow.i18n';
import { ResolvedArtifact } from '../../domain/dashboard/dashboard-task';
import { ArtifactChipComponent } from './artifact-chip.component';

/**
 * What the open task reads and what it writes, in two columns.
 *
 * The second of the task workspace's three sections, and it stays a section of its own rather than folding
 * into the checklist: the checklist answers "how do I do this", this answers "what do I need and what will
 * this produce". Both columns are already resolved by `WorkflowDashboardStore` — inputs and outputs are
 * `ArtifactDefinition` ids on the task definition, matched against the run's `ArtifactInstance[]` by
 * `artifactDefinitionId` — so this component only lays them out.
 *
 * The headings are "Reads" / "Writes" rather than "Inputs" / "Outputs": the contract's own words for these
 * fields are the artifacts a task *reads* and *produces or modifies*, and a user working the task is being
 * told what to look at, not shown a signature.
 */
@Component({
  selector: 'pp-artifact-panel',
  standalone: true,
  imports: [TranslocoPipe, ArtifactChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isEmpty()) {
      <p class="panel__empty" data-testid="artifacts-empty">{{ noneKey | transloco }}</p>
    } @else {
      <div class="panel">
        <!-- Both columns are rendered even when one is empty: "writes nothing" is information about the
             task, and a single column that silently shifted position would read as the other one. -->
        <section class="panel__column" data-testid="artifact-inputs">
          <h5 class="panel__heading">{{ inputsKey | transloco }}</h5>
          @for (artifact of inputs(); track artifact.artifactDefinitionId) {
            <pp-artifact-chip [artifact]="artifact" />
          } @empty {
            <span class="panel__dash">—</span>
          }
        </section>
        <section class="panel__column" data-testid="artifact-outputs">
          <h5 class="panel__heading">{{ outputsKey | transloco }}</h5>
          @for (artifact of outputs(); track artifact.artifactDefinitionId) {
            <pp-artifact-chip [artifact]="artifact" />
          } @empty {
            <span class="panel__dash">—</span>
          }
        </section>
      </div>
    }
  `,
  styles: `
    .panel {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 8px 24px;
    }
    .panel__column {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 6px;
      min-width: 0;
    }
    .panel__heading {
      margin: 0;
      font-size: 12px;
      font-weight: 600;
      color: #666666;
    }
    .panel__dash,
    .panel__empty {
      color: #999999;
      font-size: 12px;
      margin: 0;
    }
  `,
})
export class ArtifactPanelComponent {
  readonly inputs = input.required<ResolvedArtifact[]>();
  readonly outputs = input.required<ResolvedArtifact[]>();

  protected readonly inputsKey = `${TASK_DASHBOARD_I18N_SCOPE}.artifacts_in`;
  protected readonly outputsKey = `${TASK_DASHBOARD_I18N_SCOPE}.artifacts_out`;
  protected readonly noneKey = `${TASK_DASHBOARD_I18N_SCOPE}.artifacts_none`;

  /** A task that declares neither gets one sentence instead of two empty columns. */
  protected readonly isEmpty = computed(() => this.inputs().length === 0 && this.outputs().length === 0);
}
