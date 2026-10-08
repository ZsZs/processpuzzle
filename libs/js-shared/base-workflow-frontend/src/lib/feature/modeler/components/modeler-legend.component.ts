import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { EventDirection } from '../../../domain/definition/workflow';
import { modelerElementNameKey } from '../../../domain/modeler/modeler-element-names';
import { modelerIconUrl } from '../../../domain/modeler/modeler-icons';
import { WorkflowElementKind } from '../../../domain/modeler/workflow-graph';

/**
 * Which symbol means what, for the kinds one perspective draws.
 *
 * A read-only diagram's answer to base-state's palette rail: there is nothing to drag onto this canvas, but
 * a reader still has to be able to tell a role's symbol from an artifact's without clicking one. The kinds
 * are an input rather than all five, so each perspective explains only what it draws — the Tasks diagram
 * will pass a longer list and reuse this unchanged.
 *
 * Each name comes from the entity's own `_self` key through {@link modelerElementNameKey}, so what the
 * legend calls a role is what the Roles screen calls it.
 *
 * The one kind drawn with two symbols — an intermediate `event`, filled when thrown and outlined when
 * caught — is explained as two entries, throw then catch, so a perspective passes `event` once.
 */
@Component({
  selector: 'pp-modeler-legend',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    @for (entry of entries(); track entry.testId) {
      <span class="legend__item" [attr.data-testid]="entry.testId">
        <img class="legend__symbol" [src]="iconUrl(entry.kind, entry.direction)" alt="" aria-hidden="true" />
        {{ nameKey(entry.kind, entry.direction) | transloco }}
      </span>
    }
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      gap: 16px;
      font-size: 12px;
      color: #666666;
    }
    .legend__item {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    /* The same contained box the nodes draw their symbol in, scaled down. */
    .legend__symbol {
      width: 16px;
      height: 20px;
      object-fit: contain;
    }
  `,
})
export class ModelerLegendComponent {
  /** The kinds to explain, in the order they should be read. */
  readonly kinds = input.required<WorkflowElementKind[]>();

  /** One entry per symbol: each kind, an `event` split into its throw and its catch. */
  protected readonly entries = computed<LegendEntry[]>(() =>
    this.kinds().flatMap((kind): LegendEntry[] =>
      kind === 'event'
        ? [EventDirection.THROW, EventDirection.CATCH].map((direction) => ({ kind, direction, testId: `modeler-legend-event-${direction.toLowerCase()}` }))
        : [{ kind, testId: `modeler-legend-${kind}` }],
    ),
  );

  protected readonly iconUrl = modelerIconUrl;
  protected readonly nameKey = modelerElementNameKey;
}

interface LegendEntry {
  kind: WorkflowElementKind;
  direction?: EventDirection;
  testId: string;
}
