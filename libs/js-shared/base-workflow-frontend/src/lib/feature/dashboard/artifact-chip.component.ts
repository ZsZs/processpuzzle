import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { TASK_DASHBOARD_I18N_SCOPE } from '../../base-workflow.i18n';
import { ResolvedArtifact } from '../../domain/dashboard/dashboard-task';
import { modelerIconUrl } from '../../domain/modeler/modeler-icons';

/**
 * One artifact a task reads or writes, as a chip.
 *
 * Three treatments, which are the three states of {@link ResolvedArtifact} — and all three are
 * **neutral**, with no good/bad coloring:
 *
 * 1. an instance exists and carries a state — `{type} · {currentState}`, the literal string from the API;
 * 2. an instance exists with no state — only the type, because no state machine is attached to it;
 * 3. nothing has produced it yet — dashed, "not created yet", and *kept in the list* rather than hidden,
 *    since a declared output that does not exist is what tells the user what this task is for.
 *
 * **Why no color-coding by meaning.** `ArtifactInstance.currentState` is whatever the attached base-state
 * machine's author named it — "draft", "pending review", "under_investigation" — and neither
 * `base-workflow-api.yaml` nor the base-state model says whether a given name is good, bad or neutral.
 * Pattern-matching the string ("approved" → green) breaks silently the first time a machine uses different
 * vocabulary, and puts business meaning in a component. Doing it properly is a contract change: an
 * outcome/category field on base-state's state definition, surfaced through `ArtifactInstance` beside
 * `currentState` (open-questions #4). Until then neutral is the honest rendering — see
 * {@link TaskStatusBadgeComponent} for the case where coloring *is* warranted, and why.
 *
 * **The one mark that is not neutral is a missed required state**, and it does not contradict the above: when
 * the workflow names the state a task expects an input in or leaves an output in, the chip shows it, and
 * flags the chip when the artifact is not there yet. That compares two strings the workflow's author wrote
 * against each other; it says nothing about whether either state is a good one.
 *
 * The name is a link to the artifact — its record when the run has one, its definition otherwise — whenever
 * the host mounts a screen for it; see `DashboardLinkResolver`.
 *
 * The symbol is the modeler's own `Artifact.svg`, so an artifact looks the same here as on the diagram.
 */
@Component({
  selector: 'pp-artifact-chip',
  standalone: true,
  imports: [TranslocoPipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="chip" [class.chip--pending]="!artifact().instance" [class.chip--unmet]="!artifact().satisfied" [attr.data-testid]="'artifact-chip-' + artifact().artifactDefinitionId">
      <img class="chip__symbol" [src]="symbol" alt="" aria-hidden="true" />
      @if (artifact().link; as link) {
        <a class="chip__name chip__name--link" [routerLink]="link" [queryParams]="artifact().linkQueryParams" [attr.data-testid]="'artifact-link-' + artifact().artifactDefinitionId">{{ displayName() }}</a>
      } @else {
        <span class="chip__name">{{ displayName() }}</span>
      }
      @if (artifact().instance) {
        <span class="chip__state">{{ stateText() }}</span>
      } @else {
        <span class="chip__state" data-testid="artifact-not-created">{{ notCreatedKey | transloco }}</span>
      }
      @if (artifact().requiredState; as state) {
        <span class="chip__required" [attr.data-testid]="'artifact-required-' + artifact().artifactDefinitionId">{{ requiredKey | transloco: { state } }}</span>
      }
    </span>
  `,
  styles: `
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      max-width: 100%;
      padding: 3px 10px 3px 6px;
      border: 1px solid #dddddd;
      border-radius: 12px;
      background-color: #fafafa;
      font-size: 12px;
    }
    /* Dashed and muted: the shape says "declared", the border says "not here yet". */
    .chip--pending {
      border-style: dashed;
      background-color: #ffffff;
      color: #888888;
    }
    /* A required state not reached yet: amber, the one structural mark the chip makes — see the class comment. */
    .chip--unmet {
      border-color: #c77700;
    }
    .chip__name--link {
      color: var(--pp-color-dark-blue, rgb(24, 111, 206));
    }
    .chip__required {
      color: #777777;
      font-style: italic;
      white-space: nowrap;
    }
    .chip--unmet .chip__required {
      color: #8a5300;
    }
    .chip__symbol {
      width: 14px;
      height: 16px;
      object-fit: contain;
    }
    .chip__name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .chip__state {
      color: #777777;
      white-space: nowrap;
    }
  `,
})
export class ArtifactChipComponent {
  readonly artifact = input.required<ResolvedArtifact>();

  protected readonly symbol = modelerIconUrl('artifact');
  protected readonly notCreatedKey = `${TASK_DASHBOARD_I18N_SCOPE}.not_created`;
  protected readonly requiredKey = `${TASK_DASHBOARD_I18N_SCOPE}.required_state`;

  /**
   * The chip's second segment for an artifact that exists: its type, and its state when a machine is
   * attached. The "not created yet" case is not here — it is translated, and a computed cannot reach the
   * pipe, so the template branches on `instance` instead.
   *
   * The type is lower-cased rather than translated, for the same reason the status badge is not: it is an
   * enum value, and no screen in this workspace translates one. Lower-cased only because it reads as a
   * word here rather than as a field value.
   */
  /**
   * The artifact, and the record it is when the run holds one — `ORD-0042 : Order`, in the object notation
   * the diagram uses for the same thing.
   */
  protected readonly displayName = computed(() => {
    const { name, recordName } = this.artifact();
    return recordName ? `${recordName} : ${name}` : name;
  });

  protected readonly stateText = computed(() => {
    const artifact = this.artifact();
    const type = artifact.instance?.type?.toLowerCase() ?? artifact.type?.toLowerCase() ?? '';
    const state = artifact.instance?.currentState;
    if (!state) return type;
    return type ? `${type} · ${state}` : state;
  });
}
