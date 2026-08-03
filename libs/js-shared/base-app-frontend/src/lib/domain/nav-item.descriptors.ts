import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { APP_NAV_ITEM_I18N_SCOPE } from '../base-app.i18n';
import { APP_NAV_ITEM_ENTITY_NAME, APP_PAGE_ENTITY_NAME, APP_REGION_ENTITY_NAME } from './app-entity-names';

export { APP_NAV_ITEM_ENTITY_NAME };

function createNavItemAttrDescriptors(): AbstractAttrDescriptor[] {
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id');
  idAttr.required = true;

  const labelAttr = new BaseEntityAttrDescriptor('label', FormControlType.TEXT_BOX, 'Label', undefined, true);
  labelAttr.required = true;
  labelAttr.isHeading = true;

  const translocoIdAttr = new BaseEntityAttrDescriptor('translocoId', FormControlType.TEXT_BOX, 'Transloco Id');
  translocoIdAttr.placeholder = 'Translation key of the label';
  translocoIdAttr.hideInTable = true;

  const iconAttr = new BaseEntityAttrDescriptor('icon', FormControlType.TEXT_BOX, 'Icon');
  iconAttr.placeholder = 'Material or FontAwesome icon name';
  iconAttr.hideInTable = true;

  // A group node has children and no page, so this stays optional.
  const pageIdAttr = new BaseEntityAttrDescriptor('pageId', FormControlType.FOREIGN_KEY, 'Page');
  pageIdAttr.linkedEntityType = APP_PAGE_ENTITY_NAME;

  const rolesAttr = new BaseEntityAttrDescriptor('roles', FormControlType.TAGS, 'Roles');
  rolesAttr.placeholder = 'Empty means any authenticated member of the organization';
  rolesAttr.hideInTable = true;

  const childrenAttr = new BaseEntityAttrDescriptor('children', FormControlType.RELATED_ENTITIES, 'Children');
  childrenAttr.linkedEntityType = APP_NAV_ITEM_ENTITY_NAME;
  childrenAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, labelAttr, translocoIdAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const targetRow = new FlexboxDescriptor([iconAttr, pageIdAttr], FlexDirection.ROW);
  targetRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, targetRow, rolesAttr, childrenAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createNavItemDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: APP_NAV_ITEM_ENTITY_NAME,
    attrDescriptors: createNavItemAttrDescriptors(),
    i18nScope: APP_NAV_ITEM_I18N_SCOPE,
    // A nav item hangs off the sidenav region, or off another nav item as a group child.
    componentParent: [APP_REGION_ENTITY_NAME, APP_NAV_ITEM_ENTITY_NAME],
    isEmbedded: true,
  });
}
