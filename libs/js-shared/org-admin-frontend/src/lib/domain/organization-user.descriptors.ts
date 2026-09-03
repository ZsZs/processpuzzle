import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { ORGANIZATION_USER_I18N_SCOPE } from '../org-admin.i18n';

/**
 * Entity name of a tenant's user.
 *
 * The route segment has to be `snakeCaseName('Organization User')` — `organization-user` — because
 * `BaseFormNavigatorSingletonStore` builds the details URL from the entity name. A mismatch fails
 * quietly: the name column stops linking and Edit navigates nowhere.
 */
export const ORGANIZATION_USER_ENTITY_NAME = 'Organization User';

function createOrganizationUserAttrDescriptors(): AbstractAttrDescriptor[] {
  const usernameAttr = new BaseEntityAttrDescriptor('username', FormControlType.TEXT_BOX, 'Username');
  usernameAttr.isHeading = true;
  usernameAttr.required = true;
  // Immutable even though the identity provider would allow a rename: it is what audit records name,
  // so the update payload has no field for it. Disabled rather than hidden — an administrator looking
  // at a user needs to see which login it is.
  usernameAttr.disabled = true;

  const emailAttr = new BaseEntityAttrDescriptor('email', FormControlType.TEXT_BOX, 'E-mail');
  emailAttr.required = true;

  const firstNameAttr = new BaseEntityAttrDescriptor('firstName', FormControlType.TEXT_BOX, 'First name');
  const lastNameAttr = new BaseEntityAttrDescriptor('lastName', FormControlType.TEXT_BOX, 'Last name');

  // The reversible way to remove someone: the account, its groups and its roles stay, but no token
  // can be obtained. Deleting instead leaves the platform's createdBy and assignee references
  // dangling, which is why this is the editable control and Delete is the exception.
  const enabledAttr = new BaseEntityAttrDescriptor('enabled', FormControlType.CHECKBOX, 'Enabled');

  const emailVerifiedAttr = new BaseEntityAttrDescriptor('emailVerified', FormControlType.CHECKBOX, 'E-mail verified');
  emailVerifiedAttr.disabled = true;

  // Read-only, and a string rather than the array: roles are assigned through their own endpoint,
  // because folding them into a profile edit would mean every name correction rewrote someone's
  // permissions. The role-assignment screen is where they change.
  const rolesAttr = new BaseEntityAttrDescriptor('roleNames', FormControlType.TEXT_BOX, 'Roles');
  rolesAttr.disabled = true;

  const createdAtAttr = new BaseEntityAttrDescriptor('createdAt', FormControlType.TEXT_BOX, 'Created');
  createdAtAttr.disabled = true;
  createdAtAttr.hideInTable = true;

  const row1 = new FlexboxDescriptor([usernameAttr, emailAttr, enabledAttr], FlexDirection.ROW);
  row1.style = { 'column-gap': '10px' };
  const row2 = new FlexboxDescriptor([firstNameAttr, lastNameAttr, emailVerifiedAttr], FlexDirection.ROW);
  row2.style = { 'column-gap': '10px' };
  const row3 = new FlexboxDescriptor([rolesAttr, createdAtAttr], FlexDirection.ROW);
  row3.style = { 'column-gap': '10px' };

  const container = new FlexboxDescriptor([row1, row2, row3], FlexDirection.COLUMN);
  container.style = { 'row-gap': '5px', width: 'fit-content' };
  return [container];
}

/**
 * The descriptor driving the user list, form, search and export.
 *
 * `titleKey` is `username`: it is the one field that is both unique and stable, so it is what a status
 * bar should name. `entityTitle` is left unset, because a non-empty one beats `titleKey` there and
 * would show the words "Organization User" where the selected person belongs.
 */
export function createOrganizationUserDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ORGANIZATION_USER_ENTITY_NAME,
    i18nScope: ORGANIZATION_USER_I18N_SCOPE,
    titleKey: 'username',
    attrDescriptors: createOrganizationUserAttrDescriptors(),
  });
}
