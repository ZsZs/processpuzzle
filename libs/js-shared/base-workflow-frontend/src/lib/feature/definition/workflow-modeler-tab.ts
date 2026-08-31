import { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { WORKFLOW_MODELER_I18N_KEY } from '../../base-workflow.i18n';
import { WorkflowModelerTabComponent } from './workflow-modeler-tab.component';

/**
 * The Workflow Modeler tab, declared once and consumed twice: {@link BASE_WORKFLOW_ROUTES} mounts it as
 * `workflow/:entityId/modeler`, and `WorkflowFacade` puts it on the descriptor so the tab bar renders the
 * link. One constant rather than two literals because the segment is what ties the link to the route — a
 * mismatch would render a tab that navigates to a URL nothing matches.
 *
 * The same `modeler` segment as {@link ROLE_MODELER_TAB}, and no conflict: a tab's segment is only ever
 * appended to its own entity's `<entity>/<id>/`, so the two live at `workflow/…/modeler` and
 * `workflow-role-definition/…/modeler`.
 *
 * `testIdSuffix` is left unset: the framework's default is already `show-modeler`.
 */
export const WORKFLOW_MODELER_TAB: EntityTabDescriptor = {
  segment: 'modeler',
  i18nKey: WORKFLOW_MODELER_I18N_KEY,
  component: WorkflowModelerTabComponent,
};
