import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { ORGANIZATION_ROLE_I18N_SCOPE } from '../org-admin.i18n';
import { OrganizationRole } from './organization-user';

/** Entity name of a tenant's role. The route segment is `organization-role`, as `snakeCaseName` gives it. */
export const ORGANIZATION_ROLE_ENTITY_NAME = 'Organization Role';

/** The shape the backend enforces for a role name. The name is the key, so it is checked before it is sent. */
export const ROLE_NAME_PATTERN = '^[a-z0-9][a-z0-9_-]{0,62}$';

function createOrganizationRoleAttrDescriptors(): AbstractAttrDescriptor[] {
  // Chosen once, at creation: every document, navigation item, workflow and token carries the name, so a
  // rename would detach them all. `createOnly` keeps it editable only while the role is new.
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.isHeading = true;
  nameAttr.isLinkToDetails = true;
  nameAttr.required = true;
  nameAttr.pattern = ROLE_NAME_PATTERN;
  nameAttr.createOnly = true;

  // Who manages the role. Derived by the backend and shown only, so a role's origin cannot be set from here.
  const originAttr = new BaseEntityAttrDescriptor('origin', FormControlType.TEXT_BOX, 'Origin');
  originAttr.disabled = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXT_BOX, 'Description');

  const row = new FlexboxDescriptor([nameAttr, originAttr], FlexDirection.ROW);
  row.style = { 'column-gap': '10px' };

  const container = new FlexboxDescriptor([row, descriptionAttr], FlexDirection.COLUMN);
  container.style = { 'row-gap': '5px', width: 'fit-content' };
  return [container];
}

/**
 * Only a role the administrator defined may be changed or deleted here. A platform role, or one a workflow
 * role definition projects, is refused, and its row says where it is managed instead: the form shows it
 * read-only, and the delete action is not offered.
 */
const administratorManaged = (entity: unknown): boolean => (entity as OrganizationRole).administratorManaged;

/** The descriptor driving the role list, form, search and export. */
export function createOrganizationRoleDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ORGANIZATION_ROLE_ENTITY_NAME,
    i18nScope: ORGANIZATION_ROLE_I18N_SCOPE,
    titleKey: 'name',
    attrDescriptors: createOrganizationRoleAttrDescriptors(),
    isEditable: administratorManaged,
    isDeletable: administratorManaged,
  });
}
