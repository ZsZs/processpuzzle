import { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { WORKFLOW_ROLE_MODELER_I18N_KEY } from '../../base-workflow.i18n';
import { RoleModelerTabComponent } from './role-modeler-tab.component';

/**
 * The Role Modeler tab, declared once and consumed twice: {@link BASE_WORKFLOW_ROUTES} mounts it as
 * `workflow-role-definition/:entityId/modeler`, and `WorkflowRoleDefinitionFacade` puts it on the
 * descriptor so the tab bar renders the link. One constant rather than two literals because the segment is
 * what ties the link to the route — a mismatch would render a tab that navigates to a URL nothing matches.
 *
 * `testIdSuffix` is left unset: the framework's default is already `show-modeler`.
 */
export const ROLE_MODELER_TAB: EntityTabDescriptor = {
  segment: 'modeler',
  i18nKey: WORKFLOW_ROLE_MODELER_I18N_KEY,
  component: RoleModelerTabComponent,
};
