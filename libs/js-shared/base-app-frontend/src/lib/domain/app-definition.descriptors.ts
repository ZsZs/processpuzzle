import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { APP_DEFINITION_I18N_SCOPE } from '../base-app.i18n';
import { AppDefinitionStatus, COLOR_SCHEMES, LAYOUT_PRESETS, MATERIAL_THEMES, SIDENAV_MODES } from './app-definition';
import { APP_DEFINITION_ENTITY_NAME, APP_PAGE_ENTITY_NAME, APP_REGION_ENTITY_NAME } from './app-entity-names';
import { APP_REGION_ID_FIELD } from './region-definition.descriptors';
import { toSelectables } from './selectables';

export { APP_DEFINITION_ENTITY_NAME };

const statusSelectables = toSelectables(Object.keys(AppDefinitionStatus));

function createAppDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id');
  idAttr.required = true;
  idAttr.placeholder = 'Unique within the organization, e.g. claims-app';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  nameAttr.isHeading = true;

  const translocoIdAttr = new BaseEntityAttrDescriptor('translocoId', FormControlType.TEXT_BOX, 'Transloco Id');
  translocoIdAttr.placeholder = 'Translation key of the application name';
  translocoIdAttr.hideInTable = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // Server-assigned: shown so the designer can see where a definition stands, never edited here.
  // Status changes through `POST .../publish`, the versions through any successful write.
  const statusAttr = new BaseEntityAttrDescriptor('status', FormControlType.DROPDOWN, 'Status', statusSelectables);
  statusAttr.disabled = true;

  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const publishedVersionAttr = new BaseEntityAttrDescriptor('publishedVersion', FormControlType.TEXT_BOX, 'Published Version');
  publishedVersionAttr.disabled = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.disabled = true;

  // region theme — flattened out of `theme` by AppDefinitionMapper
  const materialThemeAttr = new BaseEntityAttrDescriptor('materialTheme', FormControlType.DROPDOWN, 'Material Theme', toSelectables(MATERIAL_THEMES));
  materialThemeAttr.hideInTable = true;

  const colorSchemeAttr = new BaseEntityAttrDescriptor('colorScheme', FormControlType.DROPDOWN, 'Color Scheme', toSelectables(COLOR_SCHEMES));
  colorSchemeAttr.hideInTable = true;

  const logoUrlAttr = new BaseEntityAttrDescriptor('logoUrl', FormControlType.TEXT_BOX, 'Logo Url');
  logoUrlAttr.placeholder = 'Tenant logo shown in the header region';
  logoUrlAttr.hideInTable = true;

  const faviconUrlAttr = new BaseEntityAttrDescriptor('faviconUrl', FormControlType.TEXT_BOX, 'Favicon Url');
  faviconUrlAttr.hideInTable = true;

  // An open key/value map by contract — keys are `--pp-*` token names, values any CSS color — so a
  // key/value editor is the only shape that can carry it without the form inventing a closed list.
  const tokenOverridesAttr = new BaseEntityAttrDescriptor('tokenOverrides', FormControlType.ADDITIONAL_PROPERTIES, 'Token Overrides');
  tokenOverridesAttr.hideInTable = true;
  // endregion

  // region layout — flattened out of `layout` by AppDefinitionMapper
  const presetAttr = new BaseEntityAttrDescriptor('preset', FormControlType.DROPDOWN, 'Layout Preset', toSelectables(LAYOUT_PRESETS));
  presetAttr.hideInTable = true;

  const sidenavModeAttr = new BaseEntityAttrDescriptor('sidenavMode', FormControlType.DROPDOWN, 'Sidenav Mode', toSelectables(SIDENAV_MODES));
  sidenavModeAttr.hideInTable = true;

  const contentMaxWidthAttr = new BaseEntityAttrDescriptor('contentMaxWidth', FormControlType.TEXT_BOX, 'Content Max Width');
  contentMaxWidthAttr.placeholder = 'CSS length, e.g. 1280px; empty means full width';
  contentMaxWidthAttr.hideInTable = true;

  const sidenavCollapsibleAttr = new BaseEntityAttrDescriptor('sidenavCollapsible', FormControlType.CHECKBOX, 'Sidenav Collapsible');
  sidenavCollapsibleAttr.hideInTable = true;

  const sidenavOpenByDefaultAttr = new BaseEntityAttrDescriptor('sidenavOpenByDefault', FormControlType.CHECKBOX, 'Sidenav Open By Default');
  sidenavOpenByDefaultAttr.hideInTable = true;
  // endregion

  // region nested graph — kept on the entity as-is by AppDefinitionMapper, so the full-replacement PUT preserves it
  const regionsAttr = new BaseEntityAttrDescriptor('regions', FormControlType.RELATED_ENTITIES, 'Regions');
  regionsAttr.linkedEntityType = APP_REGION_ENTITY_NAME;
  // A region has no `id` in the contract; `type` is what identifies it. See APP_REGION_ID_FIELD.
  regionsAttr.referenceIdField = APP_REGION_ID_FIELD;
  regionsAttr.hideInTable = true;

  const pagesAttr = new BaseEntityAttrDescriptor('pages', FormControlType.RELATED_ENTITIES, 'Pages');
  pagesAttr.linkedEntityType = APP_PAGE_ENTITY_NAME;
  pagesAttr.hideInTable = true;
  // endregion

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr, translocoIdAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const revisionRow = new FlexboxDescriptor([statusAttr, versionAttr, publishedVersionAttr, updatedAtAttr], FlexDirection.ROW);
  revisionRow.style = { 'column-gap': '10px' };
  const themeRow = new FlexboxDescriptor([materialThemeAttr, colorSchemeAttr], FlexDirection.ROW);
  themeRow.style = { 'column-gap': '10px' };
  const brandingRow = new FlexboxDescriptor([logoUrlAttr, faviconUrlAttr], FlexDirection.ROW);
  brandingRow.style = { 'column-gap': '10px' };
  const layoutRow = new FlexboxDescriptor([presetAttr, sidenavModeAttr, contentMaxWidthAttr], FlexDirection.ROW);
  layoutRow.style = { 'column-gap': '10px' };
  const sidenavRow = new FlexboxDescriptor([sidenavCollapsibleAttr, sidenavOpenByDefaultAttr], FlexDirection.ROW);
  sidenavRow.style = { 'column-gap': '10px' };
  const graphRow = new FlexboxDescriptor([regionsAttr, pagesAttr], FlexDirection.ROW);
  graphRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor(
    [identityRow, revisionRow, descriptionAttr, themeRow, brandingRow, tokenOverridesAttr, layoutRow, sidenavRow, graphRow],
    FlexDirection.COLUMN,
  );
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createAppDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({ entityName: APP_DEFINITION_ENTITY_NAME, attrDescriptors: createAppDefinitionAttrDescriptors(), i18nScope: APP_DEFINITION_I18N_SCOPE });
}
