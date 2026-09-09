import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { WorkflowEdgeData } from '../../../domain/modeler/workflow-graph';
import { PROPERTIES_I18N_SCOPE } from './workflow-element-properties-panel.component';

/**
 * The selected relation's properties, read-only for the same reason
 * {@link WorkflowElementPropertiesPanelComponent} is — and one more besides: an edge here is not an object
 * at all. A sequence edge is a `dependsOn` entry, an input or output edge is a row of the referenced
 * `TaskDefinition`, and an `implicit` edge is not data anywhere: it is the order two siblings are declared
 * in. There is nothing to edit in place even in principle.
 *
 * Which makes the panel's real job **saying which of the six it is**. The canvas distinguishes them by dash
 * pattern and opacity, which tells a reader that two edges differ without telling them how; the word is
 * here. `implicit` is the one worth the space: it is a real ordering that no field states, so a reader who
 * does not know that has no way to discover why two tasks appear chained.
 *
 * `label` is shown when the edge carries one — the `ANY` join marker, a tool step's operation name, a
 * required start artifact's state — because it is drawn on the edge at a size chosen for a chip rather than
 * for reading.
 */
@Component({
  selector: 'pp-workflow-relation-properties-panel',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <h3 class="pp-relation-properties__heading" data-testid="relation-heading">{{ headingKey | transloco }}</h3>

    <dl>
      <dt>{{ relationKey | transloco }}</dt>
      <dd data-testid="relation-kind">{{ relationNameKey() | transloco }}</dd>

      @if (relation().label; as label) {
        <dt>{{ labelKey | transloco }}</dt>
        <dd data-testid="relation-label">{{ label }}</dd>
      }
    </dl>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .pp-relation-properties__heading {
      margin: 0;
    }
    dl {
      display: grid;
      grid-template-columns: 1fr;
      gap: 2px;
      margin: 0;
    }
    dt {
      color: #666666;
      font-size: 12px;
    }
    dd {
      margin: 0 0 6px;
      overflow-wrap: anywhere;
    }
  `,
})
export class WorkflowRelationPropertiesPanelComponent {
  readonly relation = input.required<WorkflowEdgeData>();

  protected readonly headingKey = `${PROPERTIES_I18N_SCOPE}.relation_heading`;
  protected readonly relationKey = `${PROPERTIES_I18N_SCOPE}.relation`;
  protected readonly labelKey = `${PROPERTIES_I18N_SCOPE}.label`;

  /**
   * The word for this relation. An edge with no `relation` set is the Roles perspective's plain
   * responsibility line, which is what `unknown` names — it has no panel today, but a key beats rendering
   * the string `undefined` if one is ever given it.
   */
  protected readonly relationNameKey = computed(() => `${PROPERTIES_I18N_SCOPE}.relations.${this.relation().relation ?? 'unknown'}`);
}
