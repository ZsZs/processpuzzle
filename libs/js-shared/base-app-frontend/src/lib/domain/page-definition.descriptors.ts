import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { APP_PAGE_I18N_SCOPE } from '../base-app.i18n';
import { APP_DEFINITION_ENTITY_NAME, APP_PAGE_ENTITY_NAME, APP_WIDGET_ENTITY_NAME } from './app-entity-names';

export { APP_PAGE_ENTITY_NAME };

function createPageDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id');
  idAttr.required = true;
  idAttr.placeholder = 'Unique within the app and used verbatim as the route segment';

  const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title', undefined, true);
  titleAttr.required = true;
  titleAttr.isHeading = true;

  const translocoIdAttr = new BaseEntityAttrDescriptor('translocoId', FormControlType.TEXT_BOX, 'Transloco Id');
  translocoIdAttr.placeholder = 'Translation key of the page title';
  translocoIdAttr.hideInTable = true;

  // Contained, not referenced: `PageDefinition.widgets` is an inline array of the page, which is
  // itself an inline array of the app definition's document.
  const widgetsAttr = new BaseEntityAttrDescriptor('widgets', FormControlType.EMBEDDED_COMPONENTS, 'Widgets');
  widgetsAttr.linkedEntityType = APP_WIDGET_ENTITY_NAME;
  widgetsAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, titleAttr, translocoIdAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, widgetsAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createPageDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: APP_PAGE_ENTITY_NAME,
    attrDescriptors: createPageDefinitionAttrDescriptors(),
    i18nScope: APP_PAGE_I18N_SCOPE,
    componentParent: APP_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
