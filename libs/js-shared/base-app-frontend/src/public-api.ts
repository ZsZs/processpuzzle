/*
 * Public API Surface of @processpuzzle/base-app
 */

export {
  AppDefinition,
  AppDefinitionStatus,
  COLOR_SCHEMES,
  LAYOUT_PRESETS,
  MATERIAL_THEMES,
  NavItem,
  PageDefinition,
  REGION_TYPES,
  RegionDefinition,
  SIDENAV_MODES,
  WidgetRef,
  type ColorScheme,
  type LayoutDefinition,
  type LayoutPreset,
  type MaterialTheme,
  type RegionType,
  type SidenavMode,
  type ThemeDefinition,
} from './lib/domain/app-definition';
export {
  APP_DEFINITION_I18N_SCOPE,
  APP_NAV_ITEM_I18N_SCOPE,
  APP_PAGE_I18N_SCOPE,
  APP_REGION_I18N_SCOPE,
  APP_WIDGET_I18N_SCOPE,
  BASE_APP_TRANSLOCO_SCOPE,
} from './lib/base-app.i18n';
export { APP_DEFINITION_ENTITY_NAME, createAppDefinitionDescriptor } from './lib/domain/app-definition.descriptors';
export { APP_NAV_ITEM_ENTITY_NAME, createNavItemDescriptor } from './lib/domain/nav-item.descriptors';
export { APP_PAGE_ENTITY_NAME, createPageDefinitionDescriptor } from './lib/domain/page-definition.descriptors';
export { APP_REGION_ENTITY_NAME, APP_REGION_ID_FIELD, createRegionDefinitionDescriptor } from './lib/domain/region-definition.descriptors';
export { APP_WIDGET_ENTITY_NAME, createWidgetRefDescriptor } from './lib/domain/widget-ref.descriptors';
export { AppDefinitionMapper } from './lib/domain/app-definition.mapper';
export { AppDefinitionService } from './lib/domain/app-definition.service';
export { AppDefinitionStore } from './lib/domain/app-definition.store';
export { AppDefinitionFacade } from './lib/feature/app-definition.facade';
export { AppNavItemFacade } from './lib/feature/app-nav-item.facade';
export { AppPageFacade } from './lib/feature/app-page.facade';
export { AppRegionFacade } from './lib/feature/app-region.facade';
export { AppWidgetFacade } from './lib/feature/app-widget.facade';
export { AppDefinitionContainerComponent } from './lib/feature/app-definition-container.component';
export { BASE_APP_ENTITY_FACADES, BASE_APP_FACADE_PROVIDERS } from './lib/base-app.providers';
export { BASE_APP_ROUTES } from './lib/base-app.routes';
