import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, Selectable, SelectablesInput, toSelectables } from '@processpuzzle/base-entity';
import { createWidgetPropsAttrDescriptor, type WidgetRegistration } from '@processpuzzle/widgets';
import { APP_WIDGET_I18N_SCOPE } from '../base-app.i18n';
import { WIDGET_PLACEMENTS } from './app-definition';
import { APP_REGION_ENTITY_NAME, APP_ROUTE_ENTITY_NAME, APP_WIDGET_ENTITY_NAME } from './app-entity-names';

export { APP_WIDGET_ENTITY_NAME };

function createWidgetInstanceAttrDescriptors(widgetTypes: SelectablesInput): AbstractAttrDescriptor[] {
  // `id` rather than `type` identifies the widget: it is unique within its page or region, while a
  // page with two `entity-grid`s would otherwise show the same label twice.
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique within the page or region, e.g. widget-claims-grid';

  // `WidgetInstance.type` is a key into the frontend widget registry. The contract keeps it an open string,
  // so a new widget type needs no schema change; the form still offers only the keys that can render —
  // the ones registered in this build, passed in by the facade.
  const typeAttr = new BaseEntityAttrDescriptor('type', FormControlType.DROPDOWN, 'Type', widgetTypes);
  typeAttr.required = true;

  // Generated from the chosen type's `propsSchema`, as registered in the WIDGET_REGISTRY: one typed control per
  // prop, rebuilt when `type` changes. A type nobody registered or described gets the open key/value editor.
  const propsAttr = createWidgetPropsAttrDescriptor('props', 'type', 'Props');
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

/**
 * @param widgetTypes the options of the `type` dropdown — see {@link widgetTypeSelectables}. Without them the
 * dropdown is empty, as it should be where no widget is registered: none could render.
 */
export function createWidgetInstanceDescriptor(widgetTypes: SelectablesInput = []): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: APP_WIDGET_ENTITY_NAME,
    attrDescriptors: createWidgetInstanceAttrDescriptors(widgetTypes),
    i18nScope: APP_WIDGET_I18N_SCOPE,
    // A widget sits in a header/footer region or on a page — and only there, since it cannot be nested
    // in another widget.
    componentParent: [APP_REGION_ENTITY_NAME, APP_ROUTE_ENTITY_NAME],
    isEmbedded: true,
  });
}

/**
 * The registered widget types as dropdown options, sorted. `key` and `value` are both the registry key,
 * because the dropdown stores and displays `value` — a display name there would be saved as the type.
 */
export function widgetTypeSelectables(registry: ReadonlyMap<string, WidgetRegistration>): Selectable[] {
  return toSelectables([...registry.keys()].sort());
}
