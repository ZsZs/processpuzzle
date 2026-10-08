import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { EventDirection } from '../../../domain/definition/workflow';
import { EventMarker, modelerElementNameKey, modelerEventMarkerNameKey } from '../../../domain/modeler/modeler-element-names';
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
 * The one kind drawn with several symbols — an `event` — is explained as one entry per symbol, so a
 * perspective passes `event` once: the throw and the catch, the timer's clock, and the two boundary rings,
 * interrupting and not. A boundary entry is the catch symbol inside a small copy of the ring its node draws.
 */
@Component({
  selector: 'pp-modeler-legend',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    @for (entry of entries(); track entry.testId) {
      <span class="legend__item" [attr.data-testid]="entry.testId">
        @if (entry.ring) {
          <span class="legend__ring" [class.legend__ring--dashed]="entry.ring === 'dashed'">
            <img class="legend__symbol legend__symbol--ringed" [src]="iconUrl(entry.kind, entry.direction)" alt="" aria-hidden="true" />
          </span>
        } @else {
          <img class="legend__symbol" [src]="iconUrl(entry.kind, entry.direction, entry.marker === 'timer')" alt="" aria-hidden="true" />
        }
        {{ entry.nameKey | transloco }}
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
    /* A boundary event's double ring, scaled down: a border and an inset pseudo-element, as on the node. */
    .legend__ring {
      position: relative;
      box-sizing: border-box;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      border-radius: 50%;
      border: 1px solid var(--pp-color-dark-blue, rgb(24, 111, 206));
    }
    .legend__ring::before {
      content: '';
      position: absolute;
      inset: 1px;
      border-radius: 50%;
      border: 1px solid var(--pp-color-dark-blue, rgb(24, 111, 206));
    }
    .legend__ring--dashed,
    .legend__ring--dashed::before {
      border-style: dashed;
    }
    .legend__symbol--ringed {
      width: 12px;
      height: 6px;
    }
  `,
})
export class ModelerLegendComponent {
  /** The kinds to explain, in the order they should be read. */
  readonly kinds = input.required<WorkflowElementKind[]>();

  /** One entry per symbol: each kind, an `event` split into every symbol an event node may be drawn with. */
  protected readonly entries = computed<LegendEntry[]>(() =>
    this.kinds().flatMap((kind): LegendEntry[] => (kind === 'event' ? eventEntries() : [{ kind, nameKey: modelerElementNameKey(kind), testId: `modeler-legend-${kind}` }])),
  );

  protected readonly iconUrl = modelerIconUrl;
}

interface LegendEntry {
  kind: WorkflowElementKind;
  direction?: EventDirection;
  marker?: EventMarker;
  /** A boundary entry's ring around the symbol; absent draws the symbol alone. */
  ring?: 'solid' | 'dashed';
  nameKey: string;
  testId: string;
}

/** The event entries, in the order an event's symbols are told apart: by direction, then by what it waits on. */
function eventEntries(): LegendEntry[] {
  const kind: WorkflowElementKind = 'event';
  const marked = (marker: EventMarker, ring?: LegendEntry['ring']): LegendEntry => ({
    kind,
    direction: EventDirection.CATCH,
    marker,
    ring,
    nameKey: modelerEventMarkerNameKey(marker),
    testId: `modeler-legend-event-${marker.replaceAll('_', '-')}`,
  });
  return [
    ...[EventDirection.THROW, EventDirection.CATCH].map((direction) => ({
      kind,
      direction,
      nameKey: modelerElementNameKey(kind, direction),
      testId: `modeler-legend-event-${direction.toLowerCase()}`,
    })),
    marked('timer'),
    marked('boundary', 'solid'),
    marked('non_interrupting_boundary', 'dashed'),
  ];
}
