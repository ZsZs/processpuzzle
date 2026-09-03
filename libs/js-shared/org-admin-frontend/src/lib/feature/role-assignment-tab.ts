import type { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { ROLE_ASSIGNMENT_I18N_SCOPE } from '../org-admin.i18n';
import { RoleAssignmentComponent } from './role-assignment.component';

/** Last URL segment of the role-assignment screen: `organization-user/<userId>/roles`. */
export const ROLE_ASSIGNMENT_TAB_SEGMENT = 'roles';

/**
 * The third screen of an `Organization User`, beside the generic List and Details.
 *
 * A sibling of the details route rather than a child of it — that is what `baseEntityRoutes`'
 * `extraTabs` produces — because assigning roles is another screen *of the user*, addressed by the
 * same `<entity>/<id>` prefix, and not a part of the profile form. Keeping it out of the form is also
 * the point: roles have their own endpoint precisely so that a name correction cannot rewrite
 * somebody's permissions.
 */
export const ROLE_ASSIGNMENT_TAB: EntityTabDescriptor = {
  segment: ROLE_ASSIGNMENT_TAB_SEGMENT,
  i18nKey: `${ROLE_ASSIGNMENT_I18N_SCOPE}.tab`,
  component: RoleAssignmentComponent,
};
