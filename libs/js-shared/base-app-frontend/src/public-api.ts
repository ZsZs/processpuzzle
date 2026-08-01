/*
 * Public API Surface of @processpuzzle/base-app
 */

export {
  AppDefinition,
  AppDefinitionStatus,
  COLOR_SCHEMES,
  LAYOUT_PRESETS,
  MATERIAL_THEMES,
  REGION_TYPES,
  SIDENAV_MODES,
  type ColorScheme,
  type LayoutDefinition,
  type LayoutPreset,
  type MaterialTheme,
  type NavItem,
  type PageDefinition,
  type RegionDefinition,
  type RegionType,
  type SidenavMode,
  type ThemeDefinition,
  type WidgetRef,
} from './lib/domain/app-definition';
export { APP_DEFINITION_I18N_SCOPE, BASE_APP_TRANSLOCO_SCOPE } from './lib/base-app.i18n';
export { APP_DEFINITION_ENTITY_NAME, createAppDefinitionDescriptor } from './lib/domain/app-definition.descriptors';
export { AppDefinitionMapper } from './lib/domain/app-definition.mapper';
export { AppDefinitionService } from './lib/domain/app-definition.service';
export { AppDefinitionStore } from './lib/domain/app-definition.store';
export { AppDefinitionFacade } from './lib/feature/app-definition.facade';
export { AppDefinitionContainerComponent } from './lib/feature/app-definition-container.component';
export { BASE_APP_ROUTES } from './lib/base-app.routes';
