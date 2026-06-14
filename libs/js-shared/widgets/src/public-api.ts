/*
 * Public API Surface of @processpuzzle/widgets
 */

export { ApplicationProperty } from './app-property/app-property';
export { ApplicationPropertyStore } from './app-property/app-property.store';
export type { CardsGridSpec } from './mat-cards-grid/cards-spec';
export type { LanguageConfig, LanguageDefinition } from './transloco/language-config';
export { LanguageSelectorComponent } from './language-selector/language-selector.component';
export { LikeButtonComponent } from './like-button/like-button.component';
export { MarkdownPageComponent } from './markdown-page/markdown-page.component';
export { MatCardsGridComponent } from './mat-cards-grid/mat-cards-grid.component';
export { NavigateBackComponent } from './navigate-back/navigate-back.component';
export { NavigateBackService } from './navigate-back/navigate-back.service';
export { provideAppPropertyStore } from './app-property/app-property-store.provider';
export { provideTranslocoService } from './transloco/provide-transloco.service';
export { ShareButtonComponent } from './share-button/share-button.component';
export { ShareButtonModule } from './share-button/share-button.module';
export { TranslocoHttpLoader } from './transloco/transloco.loader';
export { widgetsRoutes } from './widgets.routes';

export * from './error-snackbar/error-snackbar.component';
export { ErrorSnackbarService, provideErrorSnackbar } from './error-snackbar/error-snackbar.service';
