import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { WORKFLOW_I18N_SCOPE } from '../../../base-workflow.i18n';
import { modelerElementNameKey } from '../../../domain/modeler/modeler-element-names';
import { modelerIconUrl } from '../../../domain/modeler/modeler-icons';
import { WorkflowNodeData } from '../../../domain/modeler/workflow-graph';

/**
 * Key root of the two properties panels' own labels. A child of the Workflow entity's `modeler` block
 * rather than a block of its own, because `base-workflow.i18n.spec.ts` asserts that every top-level block
 * of the bundle corresponds to an entity scope, and a screen's labels belong to the entity whose screen
 * it is.
 */
export const PROPERTIES_I18N_SCOPE = `${WORKFLOW_I18N_SCOPE}.modeler.properties`;

/**
 * The selected element's properties, read-only.
 *
 * Read-only because the four things a node can stand for — a role, a task, a work product, a tool — are
 * catalog aggregates of their own, each already authored on its own generated form. A panel that edited them
 * would be a second writer beside those forms, saving through four more stores, and the modeler's own Save
 * button would then mean two different things depending on what happened to be selected.
 *
 * What it shows is exactly {@link WorkflowNodeData}, which is what the converter already resolved out of the
 * catalogs — the kind, the name, the description, the id behind the node — plus the two facts the diagram
 * itself knows: whether this is a lane rather than a card, and whether the reference resolved at all.
 *
 * **`unresolved` is the row that earns its place.** A dangling id is an ordinary state of this model rather
 * than a fault (`dependsOn` and the task's own performer are authored through free TAGS controls), and the
 * card only marks it by its styling. Here it is said in words, next to the id that did not resolve — which
 * is usually the whole diagnosis.
 */
@Component({
  selector: 'pp-workflow-element-properties-panel',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <h3 class="pp-element-properties__heading">
      <img class="pp-element-properties__symbol" [src]="symbol()" [alt]="" aria-hidden="true" />
      <span data-testid="element-kind">{{ kindKey() | transloco }}</span>
    </h3>

    <dl>
      <dt>{{ nameKey | transloco }}</dt>
      <dd data-testid="element-name">{{ element().label }}</dd>

      @if (element().elementId; as elementId) {
        <dt>{{ idKey | transloco }}</dt>
        <dd class="pp-element-properties__id" data-testid="element-id">{{ elementId }}</dd>
      }

      @if (element().description; as description) {
        <dt>{{ descriptionKey | transloco }}</dt>
        <dd data-testid="element-description">{{ description }}</dd>
      }
    </dl>

    @if (element().unresolved) {
      <p class="pp-element-properties__unresolved" data-testid="element-unresolved">{{ unresolvedKey | transloco }}</p>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .pp-element-properties__heading {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 0;
    }
    .pp-element-properties__symbol {
      width: 20px;
      height: 20px;
    }
    /* A description list, because that is what this is — and it keeps the label/value pairing in the
       markup rather than only in the styling. */
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
    /* An id is a machine-readable key, and a monospace face is what stops a hyphenated one from reading
       like prose. */
    .pp-element-properties__id {
      font-family: monospace;
      font-size: 12px;
    }
    .pp-element-properties__unresolved {
      margin: 0;
      color: #b00020;
      font-size: 12px;
    }
  `,
})
export class WorkflowElementPropertiesPanelComponent {
  readonly element = input.required<WorkflowNodeData>();
  /**
   * Whether this node is a lane rather than an element card. Passed in rather than read off the data,
   * because a lane's data *is* a role's data — see {@link WorkflowSelectionService.selectedElementIsLane}.
   */
  readonly isLane = input(false);

  /**
   * The four field labels are the modeler's own rather than borrowed from one entity's scope. They have to
   * be: this panel shows a role, a task, a work product or a tool through one set of rows, so a label taken
   * from `workflow_role_definition.name` would be labelling a task's name with a role's word for it — which
   * is invisible in English and wrong in the four other languages.
   */
  protected readonly nameKey = `${PROPERTIES_I18N_SCOPE}.name`;
  protected readonly descriptionKey = `${PROPERTIES_I18N_SCOPE}.description`;
  protected readonly idKey = `${PROPERTIES_I18N_SCOPE}.id`;
  protected readonly unresolvedKey = `${PROPERTIES_I18N_SCOPE}.unresolved`;

  /**
   * What to call the selected thing. A lane is named by the modeler — it is a band of the diagram, not a
   * catalog entry — whereas everything else takes the entity's own `_self` key, so a task is called
   * whatever the Tasks list and the Task form already call it, in all five languages.
   */
  protected readonly kindKey = computed(() => (this.isLane() ? `${PROPERTIES_I18N_SCOPE}.lane` : modelerElementNameKey(this.element().kind)));

  /** The same symbol the node is drawn with, so the panel is visibly about the thing that was clicked. */
  protected readonly symbol = computed(() => modelerIconUrl(this.element().kind));
}
