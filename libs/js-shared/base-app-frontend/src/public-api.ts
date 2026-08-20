/*
 * Public API Surface of @processpuzzle/base-app
 */

export {
  AppDefinition,
  AppDefinitionStatus,
  COLOR_SCHEMES,
  ENTITY_MODES,
  LAYOUT_PRESETS,
  MATERIAL_THEMES,
  ModuleMount,
  NavItem,
  REGION_TYPES,
  RegionDefinition,
  ROUTE_TARGET_KINDS,
  RouteDefinition,
  SIDENAV_MODES,
  WIDGET_PLACEMENTS,
  WidgetInstance,
  type ColorScheme,
  type EntityMode,
  type LayoutDefinition,
  type LayoutPreset,
  type MaterialTheme,
  type RegionType,
  type RouteTarget,
  type RouteTargetKind,
  type SidenavMode,
  type ThemeDefinition,
  type WidgetPlacement,
} from './lib/domain/app-definition';
export { ModuleDefinition, moduleTranslocoScope } from './lib/domain/module-definition';
export {
  APP_DEFINITION_I18N_SCOPE,
  APP_MODULE_MOUNT_I18N_SCOPE,
  APP_NAV_ITEM_I18N_SCOPE,
  APP_PREVIEW_I18N_KEY,
  APP_REGION_I18N_SCOPE,
  APP_ROUTE_I18N_SCOPE,
  APP_WIDGET_I18N_SCOPE,
  BASE_APP_TRANSLOCO_SCOPE,
  MODULE_DEFINITION_I18N_SCOPE,
} from './lib/base-app.i18n';
export { APP_DEFINITION_ENTITY_NAME, createAppDefinitionDescriptor } from './lib/domain/app-definition.descriptors';
export { APP_MODULE_MOUNT_ENTITY_NAME, APP_MODULE_MOUNT_ID_FIELD, createModuleMountDescriptor } from './lib/domain/module-mount.descriptors';
export { APP_NAV_ITEM_ENTITY_NAME, createNavItemDescriptor } from './lib/domain/nav-item.descriptors';
export { APP_REGION_ENTITY_NAME, APP_REGION_ID_FIELD, createRegionDefinitionDescriptor } from './lib/domain/region-definition.descriptors';
export { APP_ROUTE_ENTITY_NAME, APP_ROUTE_ID_FIELD, createRouteDefinitionDescriptor } from './lib/domain/route-definition.descriptors';
export { APP_WIDGET_ENTITY_NAME, createWidgetInstanceDescriptor } from './lib/domain/widget-instance.descriptors';
export { MODULE_DEFINITION_ENTITY_NAME, createModuleDefinitionDescriptor } from './lib/domain/module-definition.descriptors';
export { AppDefinitionMapper } from './lib/domain/app-definition.mapper';
export { AppDefinitionService } from './lib/domain/app-definition.service';
export { AppDefinitionStore } from './lib/domain/app-definition.store';
export { ModuleDefinitionMapper } from './lib/domain/module-definition.mapper';
export { ModuleDefinitionService } from './lib/domain/module-definition.service';
export { ModuleDefinitionStore } from './lib/domain/module-definition.store';
export { AppDefinitionFacade } from './lib/feature/app-definition.facade';
export { ModuleDefinitionFacade } from './lib/feature/module-definition.facade';
export { AppModuleMountFacade } from './lib/feature/app-module-mount.facade';
export { AppNavItemFacade } from './lib/feature/app-nav-item.facade';
export { buildAppRoutes, type AppRouteSource, type ModuleLoader, type RouteRenderer } from './lib/feature/app-route-builder';
export { AppRouteRenderer } from './lib/feature/app-route-renderer';
export { RouteWidgetsComponent } from './lib/feature/route-widgets.component';
export { WidgetListComponent } from './lib/feature/widget-list.component';
export { RouteUnsupportedComponent } from './lib/feature/route-unsupported.component';
export { AppRegionFacade } from './lib/feature/app-region.facade';
export { AppRouteFacade } from './lib/feature/app-route.facade';
export { AppWidgetFacade } from './lib/feature/app-widget.facade';
export { AppDefinitionContainerComponent } from './lib/feature/app-definition-container.component';
export { AppPreviewComponent } from './lib/feature/app-preview.component';
// The run-time shell. Published because the standalone runtime host mounts `AppShellComponent` exactly as
// the Preview tab does — that being the whole reason the shell is a component with one input.
export { AppShellComponent } from './lib/feature/shell/app-shell.component';
export { AppShellRoutesFactory, appShellRoutesGuard } from './lib/feature/shell/app-shell-routes';
export { AppRegionRenderer, type RegionView } from './lib/feature/shell/app-region.renderer';
export { layoutOf, themeVarsOf, type ResolvedLayout } from './lib/feature/shell/app-shell.model';
export { RegionFooterComponent } from './lib/feature/shell/region-footer.component';
export { RegionHeaderComponent } from './lib/feature/shell/region-header.component';
export { RegionNavComponent, type NavOrientation } from './lib/feature/shell/region-nav.component';
export { APP_PREVIEW_TAB } from './lib/feature/app-preview-tab';
export { ModuleDefinitionContainerComponent } from './lib/feature/module-definition-container.component';
export { BASE_APP_ENTITY_FACADES, BASE_APP_FACADE_PROVIDERS } from './lib/base-app.providers';
export { BASE_APP_ROUTES } from './lib/base-app.routes';
export { BASE_APP_TRANSLATION_SOURCE } from './lib/base-app.i18n';
