import { Provider } from '@angular/core';
import type { BaseEntityFacadeRegistry } from '@processpuzzle/base-entity';
import { APP_LOGO_WIDGET_REGISTRATION } from './app-logo/app-logo.widget';
import { APP_TITLE_WIDGET_REGISTRATION } from './app-title/app-title.widget';
import { NAV_BAR_WIDGET_REGISTRATION } from './nav-bar/nav-bar.widget';
import { NAV_MENU_WIDGET_REGISTRATION } from './nav-menu/nav-menu.widget';
import { NAVIGATE_BACK_WIDGET_REGISTRATION } from './navigate-back/navigate-back.widget';
import { CARDS_GRID_WIDGET_REGISTRATION } from './mat-cards-grid/cards-grid.widget';
import { COPYRIGHT_WIDGET_REGISTRATION } from './copyright/copyright.widget';
import { DESIGN_BUTTON_WIDGET_REGISTRATION } from './design-button/design-button.widget';
import { IMAGE_ZOOM_WIDGET_REGISTRATION } from './image-zoom/image-zoom.widget';
import { LANGUAGE_SELECTOR_WIDGET_REGISTRATION } from './language-selector/language-selector.widget';
import { LIKE_BUTTON_WIDGET_REGISTRATION } from './like-button/like-button.widget';
import { MARKDOWN_PAGE_WIDGET_REGISTRATION } from './markdown-page/markdown-page.widget';
import { PHOTO_ALBUM_WIDGET_REGISTRATION } from './photo-album/photo-album.widget';
import { SHARE_BUTTON_WIDGET_REGISTRATION } from './share-button/share-button.widget';
import { SPACER_WIDGET_REGISTRATION } from './spacer/spacer.widget';
import { THEMES_BUTTON_WIDGET_REGISTRATION } from './themes-button/themes-button.widget';
import { VERSION_BUTTON_WIDGET_REGISTRATION } from './version-button/version-button.widget';
import { WidgetDefinitionFacade } from './widget-definition/widget-definition.facade';
import { WIDGET_DEFINITION_ENTITY_NAME, WIDGET_INPUT_PORT_ENTITY_NAME, WIDGET_OUTPUT_PORT_ENTITY_NAME } from './widget-definition/widget-entity-names';
import { WidgetInputPortFacade } from './widget-definition/widget-input-port.facade';
import { WidgetOutputPortFacade } from './widget-definition/widget-output-port.facade';
import { provideWidget, WidgetRegistration } from './widget-registry/widget-registry.token';

export { APP_LOGO_WIDGET } from './app-logo/app-logo.widget';
export { APP_TITLE_WIDGET } from './app-title/app-title.widget';
export { CARDS_GRID_WIDGET } from './mat-cards-grid/cards-grid.widget';
export { COPYRIGHT_WIDGET } from './copyright/copyright.widget';
export { DESIGN_BUTTON_WIDGET } from './design-button/design-button.widget';
export { IMAGE_ZOOM_WIDGET } from './image-zoom/image-zoom.widget';
export { LANGUAGE_SELECTOR_WIDGET } from './language-selector/language-selector.widget';
export { LIKE_BUTTON_WIDGET } from './like-button/like-button.widget';
export { MARKDOWN_PAGE_WIDGET } from './markdown-page/markdown-page.widget';
export { NAV_BAR_WIDGET } from './nav-bar/nav-bar.widget';
export { NAV_MENU_WIDGET } from './nav-menu/nav-menu.widget';
export { NAVIGATE_BACK_WIDGET } from './navigate-back/navigate-back.widget';
export { PHOTO_ALBUM_WIDGET } from './photo-album/photo-album.widget';
export { SHARE_BUTTON_WIDGET } from './share-button/share-button.widget';
export { SPACER_WIDGET } from './spacer/spacer.widget';
export { THEMES_BUTTON_WIDGET } from './themes-button/themes-button.widget';
export { VERSION_BUTTON_WIDGET } from './version-button/version-button.widget';

/**
 * Registry keys for this library's widgets, and the `provide*Widget()` call per key.
 *
 * Each key and its {@link WidgetRegistration} — component plus description — is declared in the widget's
 * own `<name>.widget.ts`, beside the component, so that the description is edited where the inputs it
 * describes are. `widget-contract.spec.ts` holds every one of them to its component and to the seeded
 * catalogue.
 *
 * A key is what a `WidgetInstance.type` names, so it is part of the contract with every stored
 * AppDefinition and document — **renaming one silently orphans every instance that references it**,
 * which is why they are declared as constants rather than typed inline at each call site.
 *
 * The keys are semantic, not implementation names: `cards-grid`, not `mat-cards-grid`. That the grid
 * happens to be built from Material cards is not something a designer choosing a widget should have
 * to know, and it is not something we want to be held to if the implementation changes.
 *
 * Registration is deliberately opt-in per widget rather than one blanket call: an application that
 * wants a document to be able to embed a share button but not a language selector should be able to
 * say so. {@link provideBaseWidgets} is the convenience for the common "register them all" case.
 */

export function provideAppLogoWidget(): Provider[] {
  return provideWidget(APP_LOGO_WIDGET_REGISTRATION);
}

export function provideAppTitleWidget(): Provider[] {
  return provideWidget(APP_TITLE_WIDGET_REGISTRATION);
}

export function provideNavBarWidget(): Provider[] {
  return provideWidget(NAV_BAR_WIDGET_REGISTRATION);
}

export function provideNavMenuWidget(): Provider[] {
  return provideWidget(NAV_MENU_WIDGET_REGISTRATION);
}

export function provideNavigateBackWidget(): Provider[] {
  return provideWidget(NAVIGATE_BACK_WIDGET_REGISTRATION);
}

export function provideCardsGridWidget(): Provider[] {
  return provideWidget(CARDS_GRID_WIDGET_REGISTRATION);
}

export function provideCopyrightWidget(): Provider[] {
  return provideWidget(COPYRIGHT_WIDGET_REGISTRATION);
}

export function provideDesignButtonWidget(): Provider[] {
  return provideWidget(DESIGN_BUTTON_WIDGET_REGISTRATION);
}

export function provideImageZoomWidget(): Provider[] {
  return provideWidget(IMAGE_ZOOM_WIDGET_REGISTRATION);
}

export function provideLanguageSelectorWidget(): Provider[] {
  return provideWidget(LANGUAGE_SELECTOR_WIDGET_REGISTRATION);
}

export function provideLikeButtonWidget(): Provider[] {
  return provideWidget(LIKE_BUTTON_WIDGET_REGISTRATION);
}

export function provideMarkdownPageWidget(): Provider[] {
  return provideWidget(MARKDOWN_PAGE_WIDGET_REGISTRATION);
}

export function providePhotoAlbumWidget(): Provider[] {
  return provideWidget(PHOTO_ALBUM_WIDGET_REGISTRATION);
}

export function provideShareButtonWidget(): Provider[] {
  return provideWidget(SHARE_BUTTON_WIDGET_REGISTRATION);
}

export function provideSpacerWidget(): Provider[] {
  return provideWidget(SPACER_WIDGET_REGISTRATION);
}

export function provideThemesButtonWidget(): Provider[] {
  return provideWidget(THEMES_BUTTON_WIDGET_REGISTRATION);
}

export function provideVersionButtonWidget(): Provider[] {
  return provideWidget(VERSION_BUTTON_WIDGET_REGISTRATION);
}

/** Every registration this library ships, in key order — what {@link provideBaseWidgets} registers. */
export const BASE_WIDGET_REGISTRATIONS: readonly WidgetRegistration[] = [
  APP_LOGO_WIDGET_REGISTRATION,
  APP_TITLE_WIDGET_REGISTRATION,
  CARDS_GRID_WIDGET_REGISTRATION,
  COPYRIGHT_WIDGET_REGISTRATION,
  DESIGN_BUTTON_WIDGET_REGISTRATION,
  IMAGE_ZOOM_WIDGET_REGISTRATION,
  LANGUAGE_SELECTOR_WIDGET_REGISTRATION,
  LIKE_BUTTON_WIDGET_REGISTRATION,
  MARKDOWN_PAGE_WIDGET_REGISTRATION,
  NAV_BAR_WIDGET_REGISTRATION,
  NAV_MENU_WIDGET_REGISTRATION,
  NAVIGATE_BACK_WIDGET_REGISTRATION,
  PHOTO_ALBUM_WIDGET_REGISTRATION,
  SHARE_BUTTON_WIDGET_REGISTRATION,
  SPACER_WIDGET_REGISTRATION,
  THEMES_BUTTON_WIDGET_REGISTRATION,
  VERSION_BUTTON_WIDGET_REGISTRATION,
];

/**
 * Registers every widget this library ships. Composes with any other `provideWidget()` call —
 * including ones from an aggregator's own lib, such as base-document's `document-viewer` — because
 * the registry merges through Angular's `@Optional() @SkipSelf()` resolution rather than replacing.
 */
export function provideBaseWidgets(): Provider[] {
  return BASE_WIDGET_REGISTRATIONS.flatMap((registration) => provideWidget(registration));
}

/**
 * The facades of the widget-catalogue authoring graph, to be spread into the application's `providers`.
 *
 * Nothing to do with the widget *registry* above: those calls register components a container can render,
 * these register the entities the catalogue itself is edited through. A `WidgetDefinition` describes a
 * widget type; `provideWidget` supplies the code one is implemented by.
 *
 * The embedded port facades are here for the same reason the routable one is: an embedded entity has a
 * facade like any other — that is what gives it a store — and only its repository differs, reading and
 * writing the `WidgetDefinition` payload rather than an endpoint of its own.
 */
export const BASE_WIDGET_FACADE_PROVIDERS: Provider[] = [WidgetDefinitionFacade, WidgetInputPortFacade, WidgetOutputPortFacade];

/**
 * The same facades keyed by entity name, to be spread into the application's `BASE_ENTITY_FACADE_REGISTRY`
 * value.
 *
 * Every entity an `EMBEDDED_COMPONENTS` attribute of this library names has to appear here, or the control
 * throws on first render rather than showing a list whose rows go nowhere on save — the registry is how it
 * reaches the child's store and descriptor. Spread rather than provided separately, because the token holds
 * one value: a second `provide: BASE_ENTITY_FACADE_REGISTRY` would replace the application's own entities
 * instead of adding to them.
 */
export const BASE_WIDGET_ENTITY_FACADES: BaseEntityFacadeRegistry = {
  [WIDGET_DEFINITION_ENTITY_NAME]: WidgetDefinitionFacade,
  [WIDGET_INPUT_PORT_ENTITY_NAME]: WidgetInputPortFacade,
  [WIDGET_OUTPUT_PORT_ENTITY_NAME]: WidgetOutputPortFacade,
};
