import { Provider } from '@angular/core';
import { LanguageSelectorComponent } from './language-selector/language-selector.component';
import { LikeButtonComponent } from './like-button/like-button.component';
import { MarkdownPageComponent } from './markdown-page/markdown-page.component';
import { MatCardsGridComponent } from './mat-cards-grid/mat-cards-grid.component';
import { ShareButtonComponent } from './share-button/share-button.component';
import { VersionButtonComponent } from './version-button/version-button.component';
import { provideWidget } from './widget-registry/widget-registry.token';

/**
 * Registry keys for this library's widgets, and the `provide*Widget()` call per key.
 *
 * A key is what a `WidgetInstance.type` names, so it is part of the contract with every stored
 * AppDefinition and document — **renaming one silently orphans every instance that references it**,
 * which is why they are declared here as constants rather than typed inline at each call site.
 *
 * The keys are semantic, not implementation names: `cards-grid`, not `mat-cards-grid`. That the grid
 * happens to be built from Material cards is not something a designer choosing a widget should have
 * to know, and it is not something we want to be held to if the implementation changes.
 *
 * Registration is deliberately opt-in per widget rather than one blanket call: an application that
 * wants a document to be able to embed a share button but not a language selector should be able to
 * say so. {@link provideBaseWidgets} is the convenience for the common "register them all" case.
 */
export const CARDS_GRID_WIDGET = 'cards-grid';
export const LANGUAGE_SELECTOR_WIDGET = 'language-selector';
export const LIKE_BUTTON_WIDGET = 'like-button';
export const MARKDOWN_PAGE_WIDGET = 'markdown-page';
export const SHARE_BUTTON_WIDGET = 'share-button';
export const VERSION_BUTTON_WIDGET = 'version-button';

export function provideCardsGridWidget(): Provider[] {
  return provideWidget(CARDS_GRID_WIDGET, MatCardsGridComponent);
}

export function provideLanguageSelectorWidget(): Provider[] {
  return provideWidget(LANGUAGE_SELECTOR_WIDGET, LanguageSelectorComponent);
}

export function provideLikeButtonWidget(): Provider[] {
  return provideWidget(LIKE_BUTTON_WIDGET, LikeButtonComponent);
}

export function provideMarkdownPageWidget(): Provider[] {
  return provideWidget(MARKDOWN_PAGE_WIDGET, MarkdownPageComponent);
}

export function provideShareButtonWidget(): Provider[] {
  return provideWidget(SHARE_BUTTON_WIDGET, ShareButtonComponent);
}

export function provideVersionButtonWidget(): Provider[] {
  return provideWidget(VERSION_BUTTON_WIDGET, VersionButtonComponent);
}

/**
 * Registers every widget this library ships. Composes with any other `provideWidget()` call —
 * including ones from an aggregator's own lib, such as base-document's `document-viewer` — because
 * the registry merges through Angular's `@Optional() @SkipSelf()` resolution rather than replacing.
 */
export function provideBaseWidgets(): Provider[] {
  return [
    provideCardsGridWidget(),
    provideLanguageSelectorWidget(),
    provideLikeButtonWidget(),
    provideMarkdownPageWidget(),
    provideShareButtonWidget(),
    provideVersionButtonWidget(),
  ];
}
