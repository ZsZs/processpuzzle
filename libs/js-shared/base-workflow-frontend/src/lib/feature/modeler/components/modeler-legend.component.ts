import { Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
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
 */
@Component({
  selector: 'pp-modeler-legend',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    @for (kind of kinds(); track kind) {
      <span class="legend__item" [attr.data-testid]="'modeler-legend-' + kind">
        <img class="legend__symbol" [src]="iconUrl(kind)" alt="" aria-hidden="true" />
        {{ nameKey(kind) | transloco }}
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

  protected readonly iconUrl = modelerIconUrl;
  protected readonly nameKey = modelerElementNameKey;
}
