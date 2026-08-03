import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { APP_WIDGET_I18N_SCOPE } from '../base-app.i18n';

export const APP_WIDGET_ENTITY_NAME = 'App Widget';

function createWidgetRefAttrDescriptors(): AbstractAttrDescriptor[] {
  // `id` rather than `type` identifies the widget: it is unique within its page or region, while a
  // page with two `entity-grid`s would otherwise show the same label twice.
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique within the page or region, e.g. widget-claims-grid';

  // Deliberately a free-text box and not a dropdown: `WidgetRef.type` is a key into the frontend
  // widget registry, which the contract keeps open so new widget types need no schema change.
  const typeAttr = new BaseEntityAttrDescriptor('type', FormControlType.TEXT_BOX, 'Type');
  typeAttr.required = true;
  typeAttr.placeholder = 'Widget registry key, e.g. entity-grid';

  // Each widget type owns and validates its own props shape, so the only thing this form can
  // honestly offer is an open key/value editor.
  const propsAttr = new BaseEntityAttrDescriptor('props', FormControlType.ADDITIONAL_PROPERTIES, 'Props');
  propsAttr.hideInTable = true;

  const childrenAttr = new BaseEntityAttrDescriptor('children', FormControlType.COMPONENTS, 'Children');
  childrenAttr.linkedEntityType = APP_WIDGET_ENTITY_NAME;
  childrenAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, typeAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, propsAttr, childrenAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createWidgetRefDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({ entityName: APP_WIDGET_ENTITY_NAME, attrDescriptors: createWidgetRefAttrDescriptors(), i18nScope: APP_WIDGET_I18N_SCOPE });
}
