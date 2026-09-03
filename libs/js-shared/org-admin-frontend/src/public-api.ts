/*
 * Public API Surface of @processpuzzle/org-admin
 */

export { OrganizationRole, OrganizationUser, type OrganizationUserInvitation } from './lib/domain/organization-user';
export { OrganizationRoleMapper, OrganizationUserMapper } from './lib/domain/organization-user.mapper';
export { ORG_ADMIN_ORG_KEY, OrganizationRoleService, OrganizationUserService } from './lib/domain/organization-user.service';
export { OrganizationUserStore } from './lib/domain/organization-user.store';
export { ORGANIZATION_USER_ENTITY_NAME, createOrganizationUserDescriptor } from './lib/domain/organization-user.descriptors';

export { OrganizationUserFacade } from './lib/feature/organization-user.facade';
export { OrganizationUserContainerComponent } from './lib/feature/organization-user-container.component';
export { InviteUserDialog, type InviteUserDialogData, type InviteUserDialogResult } from './lib/feature/invite-user.dialog';
export { RoleAssignmentComponent } from './lib/feature/role-assignment.component';
export { ROLE_ASSIGNMENT_TAB, ROLE_ASSIGNMENT_TAB_SEGMENT } from './lib/feature/role-assignment-tab';

export {
  BASE_ENTITY_TRANSLOCO_SCOPE,
  ORGANIZATION_ROLE_I18N_SCOPE,
  ORGANIZATION_USER_I18N_SCOPE,
  ORG_ADMIN_TRANSLATION_SOURCE,
  ORG_ADMIN_TRANSLOCO_SCOPE,
  ROLE_ASSIGNMENT_I18N_SCOPE,
} from './lib/org-admin.i18n';
export { ORG_ADMIN_ENTITY_FACADES, ORG_ADMIN_FACADE_PROVIDERS, ORG_ADMIN_ROUTES } from './lib/org-admin.routes';
