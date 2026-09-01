import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TaskInstanceStatus } from '../../domain/execution/workflow-instance';

/**
 * A task's status, as a colored pill.
 *
 * **Colored, unlike the artifact chips beside it, and the difference is the contract's rather than a
 * matter of taste.** `TaskInstanceStatus` is a closed enum of five values that base-workflow itself
 * assigns, so what `BLOCKED` means is fixed and coloring it red states a fact. `ArtifactInstance.currentState`
 * is an arbitrary string from whichever base-state machine happens to be attached — see
 * {@link ArtifactChipComponent} for why that one stays neutral. Coloring both, or neither, would be the
 * inconsistency.
 *
 * The **value is shown untranslated**, on purpose: nothing in this workspace translates an enum value, so
 * the generated Task Instance list one route away shows `ACTIVE` as `ACTIVE`, and a badge here that said
 * "Aktiv" would make the two screens look like they were reading different fields.
 */
@Component({
  selector: 'pp-task-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: ` <span class="badge" [attr.data-status]="status()" [attr.data-testid]="'task-status-' + (status() ?? 'unknown')">{{ status() ?? '—' }}</span> `,
  styles: `
    .badge {
      display: inline-block;
      padding: 1px 8px;
      border-radius: 10px;
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.04em;
      white-space: nowrap;
      background-color: #eeeeee;
      color: #444444;
    }
    /* The five statuses, keyed off the attribute rather than five classes, so the template stays one line. */
    .badge[data-status='PENDING'] {
      background-color: #e8e8e8;
      color: #555555;
    }
    .badge[data-status='ACTIVE'] {
      background-color: var(--pp-color-dark-blue, rgb(24, 111, 206));
      color: var(--pp-color-white, #eeeeee);
    }
    .badge[data-status='BLOCKED'] {
      background-color: #b3261e;
      color: #ffffff;
    }
    .badge[data-status='COMPLETED'] {
      background-color: var(--pp-color-light-green, rgb(92, 218, 207));
      color: #10403c;
    }
    /* Skipped finished without being done, so it is muted rather than green — the one status the board
       folds into another column while the card keeps saying which it was. */
    .badge[data-status='SKIPPED'] {
      background-color: #ffffff;
      color: #666666;
      box-shadow: inset 0 0 0 1px #cccccc;
    }
  `,
})
export class TaskStatusBadgeComponent {
  readonly status = input<TaskInstanceStatus | undefined>();
}
