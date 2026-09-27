/*
 * Public API Surface of @processpuzzle/widgets
 */

export { ApplicationProperty } from './app-property/app-property';
export {
  BASE_WIDGET_ENTITY_FACADES,
  BASE_WIDGET_FACADE_PROVIDERS,
  APP_LOGO_WIDGET,
  APP_TITLE_WIDGET,
  BASE_WIDGET_REGISTRATIONS,
  CARDS_GRID_WIDGET,
  COPYRIGHT_WIDGET,
  DESIGN_BUTTON_WIDGET,
  IMAGE_ZOOM_WIDGET,
  LANGUAGE_SELECTOR_WIDGET,
  LIKE_BUTTON_WIDGET,
  MARKDOWN_PAGE_WIDGET,
  NAV_MENU_WIDGET,
  NAVIGATE_BACK_WIDGET,
  PHOTO_ALBUM_WIDGET,
  SHARE_BUTTON_WIDGET,
  THEMES_BUTTON_WIDGET,
  VERSION_BUTTON_WIDGET,
  provideAppLogoWidget,
  provideAppTitleWidget,
  provideBaseWidgets,
  provideCardsGridWidget,
  provideCopyrightWidget,
  provideDesignButtonWidget,
  provideImageZoomWidget,
  provideLanguageSelectorWidget,
  provideLikeButtonWidget,
  provideMarkdownPageWidget,
  provideNavMenuWidget,
  provideNavigateBackWidget,
  providePhotoAlbumWidget,
  provideShareButtonWidget,
  provideThemesButtonWidget,
  provideVersionButtonWidget,
} from './base-widget.providers';
export { ApplicationPropertyStore } from './app-property/app-property.store';
export { CopyrightComponent } from './copyright/copyright.component';
export type { CardsGridSpec } from './mat-cards-grid/cards-spec';
export type { MenuItemSpec } from './mat-cards-grid/menu-item-spec';
export { DesignButtonComponent } from './design-button/design-button.component';
export { ImageZoomComponent } from './image-zoom/image-zoom.component';
export { PhotoAlbumComponent } from './photo-album/photo-album.component';
export type { PhotoAlbumImage } from './photo-album/photo-album.component';
export { LanguageSelectorComponent } from './language-selector/language-selector.component';
export { LikeButtonComponent } from './like-button/like-button.component';
export { MarkdownPageComponent } from './markdown-page/markdown-page.component';
export { MatCardsGridComponent } from './mat-cards-grid/mat-cards-grid.component';
export { provideAppPropertyStore } from './app-property/app-property-store.provider';
export { ShareButtonComponent } from './share-button/share-button.component';
export { ShareButtonModule } from './share-button/share-button.module';
export { SidenavAutosizeDirective } from './sidenav-autosize/sidenav-autosize.directive';
export {
  DEFAULT_THEME_COLOR_SCHEME,
  DEFAULT_THEME_PRESET,
  isThemeColorScheme,
  isThemePreset,
  THEME_COLOR_SCHEMES,
  THEME_PRESET_SWATCHES,
  THEME_PRESETS,
  themeClassesFor,
} from './themes-button/theme-presets';
export type { ThemeColorScheme, ThemePreset, ThemeSelection } from './themes-button/theme-presets';
export { DOCUMENT_THEME_STORAGE_KEY, ThemeService } from './themes-button/theme.service';
export { ThemesButtonComponent } from './themes-button/themes-button.component';
export { VersionButtonComponent } from './version-button/version-button.component';
export { WIDGET_PLACEMENTS, WidgetInstance, WidgetPlacement } from './widget-registry/widget-instance';
export { hasDescribedProps, propsSchemaToDescriptors } from './widget-definition/props-schema-to-descriptors';
export { ATTRIBUTE_VISIBILITY_MODES, InputPort, OutputPort, PORT_TYPES, WIDGET_DEFINITION_STATUSES, WidgetDefinition } from './widget-definition/widget-definition';
export type { AttributeVisibility, AttributeVisibilityMode, PortType, PropsSchema, PropsSchemaProperty, WidgetDefinitionStatus } from './widget-definition/widget-definition';
export {
  BASE_ENTITY_TRANSLOCO_SCOPE,
  BASE_WIDGET_TRANSLOCO_SCOPE,
  PUBLISH_BUTTON_I18N_KEY,
  PUBLISH_TOOLTIP_I18N_KEY,
  WIDGET_DEFINITION_I18N_SCOPE,
  WIDGET_INPUT_PORT_I18N_SCOPE,
  WIDGET_OUTPUT_PORT_I18N_SCOPE,
} from './base-widget.i18n';
export { BASE_WIDGET_ROUTES } from './base-widget.routes';
export { createWidgetDefinitionDescriptor, WIDGET_KEY_PATTERN } from './widget-definition/widget-definition.descriptors';
export { createWidgetInputPortDescriptor, createWidgetOutputPortDescriptor, WIDGET_PORT_ID_FIELD } from './widget-definition/widget-port.descriptors';
export { WIDGET_DEFINITION_ENTITY_NAME, WIDGET_INPUT_PORT_ENTITY_NAME, WIDGET_OUTPUT_PORT_ENTITY_NAME } from './widget-definition/widget-entity-names';
export { WidgetDefinitionContainerComponent } from './widget-definition/widget-definition-container.component';
export { WidgetDefinitionFacade } from './widget-definition/widget-definition.facade';
export { WidgetDefinitionMapper } from './widget-definition/widget-definition.mapper';
export { WidgetDefinitionService } from './widget-definition/widget-definition.service';
export { WidgetDefinitionPublishStore, WidgetDefinitionStore } from './widget-definition/widget-definition.store';
export { WidgetInputPortFacade } from './widget-definition/widget-input-port.facade';
export { WidgetOutputPortFacade } from './widget-definition/widget-output-port.facade';
export { WIDGET_REGISTRY, provideWidget } from './widget-registry/widget-registry.token';
export type { WidgetDescription, WidgetRegistration } from './widget-registry/widget-registry.token';
export { WidgetHostComponent } from './widget-host/widget-host.component';
export { resolveWidgetInputs, widgetOutputBindings } from './widget-host/widget-bindings';
export type { HostedWidget, WidgetBindingResolver, WidgetPortEvent } from './widget-host/widget-bindings';
export { widgetsRoutes } from './widgets.routes';
export { BASE_WIDGET_TRANSLATION_SOURCE } from './base-widget.i18n';
export { APPLICATION_CONTEXT } from './app-context/application-context';
export type { ApplicationContext, NavMenuItem } from './app-context/application-context';
export { AppLogoComponent } from './app-logo/app-logo.component';
export { AppTitleComponent } from './app-title/app-title.component';
export { NAV_MENU_VISIBILITIES, NavMenuComponent } from './nav-menu/nav-menu.component';
export type { NavMenuVisibility } from './nav-menu/nav-menu.component';
export { ARTIFACT_FORMAT } from './widget-definition/props-schema-to-descriptors';
export { createWidgetPropsAttrDescriptor, WidgetPropsAttrDescriptor, WidgetPropsControlComponent } from './widget-props/widget-props-control.component';
