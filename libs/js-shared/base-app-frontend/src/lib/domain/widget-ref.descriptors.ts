import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { APP_WIDGET_I18N_SCOPE } from '../base-app.i18n';
import { WIDGET_PLACEMENTS } from './app-definition';
import { APP_PAGE_ENTITY_NAME, APP_REGION_ENTITY_NAME, APP_WIDGET_ENTITY_NAME } from './app-entity-names';

export { APP_WIDGET_ENTITY_NAME };

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

  // Widgets do not nest: a container widget type lists the ids of siblings in `props.childIds`, and each
  // of those siblings is marked REFERENCED so it is placed by the container instead of rendering twice.
  // That is the whole of composition here — there is no child collection to edit.
  const placementAttr = new BaseEntityAttrDescriptor('placement', FormControlType.DROPDOWN, 'Placement', toSelectables(WIDGET_PLACEMENTS));
  placementAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, typeAttr, placementAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, propsAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createWidgetRefDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: APP_WIDGET_ENTITY_NAME,
    attrDescriptors: createWidgetRefAttrDescriptors(),
    i18nScope: APP_WIDGET_I18N_SCOPE,
    // A widget sits in a header/footer region or on a page — and only there, since it cannot be nested
    // in another widget.
    componentParent: [APP_REGION_ENTITY_NAME, APP_PAGE_ENTITY_NAME],
    isEmbedded: true,
  });
}
