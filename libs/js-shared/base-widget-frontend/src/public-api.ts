/*
 * Public API Surface of @processpuzzle/base-widget
 */

export { ApplicationProperty } from './app-property/app-property';
export {
  CARDS_GRID_WIDGET,
  LANGUAGE_SELECTOR_WIDGET,
  LIKE_BUTTON_WIDGET,
  MARKDOWN_PAGE_WIDGET,
  SHARE_BUTTON_WIDGET,
  VERSION_BUTTON_WIDGET,
  provideBaseWidgets,
  provideCardsGridWidget,
  provideLanguageSelectorWidget,
  provideLikeButtonWidget,
  provideMarkdownPageWidget,
  provideShareButtonWidget,
  provideVersionButtonWidget,
} from './base-widget.providers';
export { ApplicationPropertyStore } from './app-property/app-property.store';
export type { CardsGridSpec } from './mat-cards-grid/cards-spec';
export type { MenuItemSpec } from './mat-cards-grid/menu-item-spec';
export { DesignButtonComponent } from './design-button/design-button.component';
export { LanguageSelectorComponent } from './language-selector/language-selector.component';
export { LikeButtonComponent } from './like-button/like-button.component';
export { MarkdownPageComponent } from './markdown-page/markdown-page.component';
export { MatCardsGridComponent } from './mat-cards-grid/mat-cards-grid.component';
export { provideAppPropertyStore } from './app-property/app-property-store.provider';
export { ShareButtonComponent } from './share-button/share-button.component';
export { ShareButtonModule } from './share-button/share-button.module';
export { VersionButtonComponent } from './version-button/version-button.component';
export { WIDGET_PLACEMENTS, WidgetInstance, WidgetPlacement } from './widget-registry/widget-instance';
export { hasDescribedProps, propsSchemaToDescriptors } from './widget-definition/props-schema-to-descriptors';
export { ATTRIBUTE_VISIBILITY_MODES, PORT_TYPES, WIDGET_DEFINITION_STATUSES, WidgetDefinition } from './widget-definition/widget-definition';
export type { AttributeVisibility, AttributeVisibilityMode, InputPort, OutputPort, PortType, PropsSchema, PropsSchemaProperty, WidgetDefinitionStatus } from './widget-definition/widget-definition';
export { WIDGET_REGISTRY, provideWidget } from './widget-registry/widget-registry.token';
export { widgetsRoutes } from './widgets.routes';

