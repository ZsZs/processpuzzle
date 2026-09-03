import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { ORGANIZATION_I18N_SCOPE } from '../platform-admin.i18n';
import { OrganizationStatus } from './organization';

/**
 * Entity name of the tenant.
 *
 * The route segment has to be `snakeCaseName('Organization')` — `organization` — because
 * `BaseFormNavigatorSingletonStore` builds the details URL from the entity name. A mismatch does not
 * fail loudly: the Name column stops linking and Edit navigates nowhere.
 */
export const ORGANIZATION_ENTITY_NAME = 'Organization';

const statusSelectables = Object.keys(OrganizationStatus).map((key) => ({ key, value: key }));

function createOrganizationAttrDescriptors(): AbstractAttrDescriptor[] {
  const keyAttr = new BaseEntityAttrDescriptor('key', FormControlType.TEXT_BOX, 'Key');
  keyAttr.isHeading = true;
  // Immutable once claimed: it is the tenant's public URL segment and the scope of all its metadata,
  // so the contract's update payload has no field for it. Disabled rather than hidden, because an
  // operator looking at an organization needs to see which key it is.
  keyAttr.disabled = true;

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  // Read-only for the same reason the mapper does not send it: the status moves only through the
  // suspend and activate operations, each of which has a Keycloak call to make alongside the write.
  // A form that could set it directly would produce a tenant marked usable with no realm behind it.
  const statusAttr = new BaseEntityAttrDescriptor('status', FormControlType.DROPDOWN, 'Status', statusSelectables);
  statusAttr.disabled = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  const contactEmailAttr = new BaseEntityAttrDescriptor('contactEmail', FormControlType.TEXT_BOX, 'Contact e-mail');
  contactEmailAttr.placeholder = 'ops@example.com';

  const defaultLocaleAttr = new BaseEntityAttrDescriptor('defaultLocale', FormControlType.TEXT_BOX, 'Default locale');
  defaultLocaleAttr.placeholder = 'en-GB';
  defaultLocaleAttr.hideInTable = true;

  const createdAtAttr = new BaseEntityAttrDescriptor('createdAt', FormControlType.TEXT_BOX, 'Created');
  createdAtAttr.disabled = true;
  createdAtAttr.hideInTable = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated');
  updatedAtAttr.disabled = true;
  updatedAtAttr.hideInTable = true;

  const row1 = new FlexboxDescriptor([keyAttr, nameAttr, statusAttr], FlexDirection.ROW);
  row1.style = { 'column-gap': '10px' };
  const row2 = new FlexboxDescriptor([contactEmailAttr, defaultLocaleAttr], FlexDirection.ROW);
  row2.style = { 'column-gap': '10px' };
  const row3 = new FlexboxDescriptor([createdAtAttr, updatedAtAttr], FlexDirection.ROW);
  row3.style = { 'column-gap': '10px' };

  const container = new FlexboxDescriptor([row1, row2, descriptionAttr, row3], FlexDirection.COLUMN);
  container.style = { 'row-gap': '5px', width: 'fit-content' };
  return [container];
}

/**
 * The descriptor driving the organization list, form, search and PDF export.
 *
 * `titleKey` is `key` rather than `name`: an operator scanning the status bar is looking for the tenant
 * whose URL they were given, and two customers can perfectly well both be called "Acme".
 *
 * `entityTitle` is deliberately left unset. A non-empty one beats `titleKey` in the status bar, so
 * setting it would show the word "Organization" where the selected tenant's key belongs.
 *
 * `i18nScope` is spelled out even though the derived default would agree with it (`Organization` →
 * `organization`): the derived value has no `platform_admin.` prefix, and the scope this library
 * registers does.
 */
export function createOrganizationDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ORGANIZATION_ENTITY_NAME,
    i18nScope: ORGANIZATION_I18N_SCOPE,
    titleKey: 'key',
    attrDescriptors: createOrganizationAttrDescriptors(),
  });
}
