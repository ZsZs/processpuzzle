import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { APP_REGION_I18N_SCOPE } from '../base-app.i18n';
import { REGION_TYPES } from './app-definition';
import { APP_DEFINITION_ENTITY_NAME, APP_NAV_ITEM_ENTITY_NAME, APP_REGION_ENTITY_NAME, APP_WIDGET_ENTITY_NAME } from './app-entity-names';

export { APP_REGION_ENTITY_NAME };

/**
 * `RegionDefinition` has no `id` — a region is identified by the shell slot it fills, and an app
 * declares each slot at most once. Referencing attributes therefore have to set
 * `referenceIdField = 'type'`; see the `regions` attribute of the `AppDefinition` descriptor.
 */
export const APP_REGION_ID_FIELD = 'type';

function createRegionDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  const typeAttr = new BaseEntityAttrDescriptor('type', FormControlType.DROPDOWN, 'Type', toSelectables(REGION_TYPES), true);
  typeAttr.required = true;
  typeAttr.isHeading = true;

  // Per the contract, `navItems` applies to `sidenav` and `widgets` to `header` / `footer`; the
  // schema models them as optional siblings rather than a discriminated union, so both are offered
  // and it is the region's type that decides which one is meaningful. Both are contained: the rows
  // are nested arrays of this region, which is itself nested in the app definition's document.
  const navItemsAttr = new BaseEntityAttrDescriptor('navItems', FormControlType.EMBEDDED_COMPONENTS, 'Nav Items');
  navItemsAttr.linkedEntityType = APP_NAV_ITEM_ENTITY_NAME;
  navItemsAttr.hideInTable = true;

  const widgetsAttr = new BaseEntityAttrDescriptor('widgets', FormControlType.EMBEDDED_COMPONENTS, 'Widgets');
  widgetsAttr.linkedEntityType = APP_WIDGET_ENTITY_NAME;
  widgetsAttr.hideInTable = true;

  const flexBoxContainer = new FlexboxDescriptor([typeAttr, navItemsAttr, widgetsAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createRegionDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: APP_REGION_ENTITY_NAME,
    attrDescriptors: createRegionDefinitionAttrDescriptors(),
    i18nScope: APP_REGION_I18N_SCOPE,
    componentParent: APP_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
