import { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { APP_PREVIEW_I18N_KEY } from '../base-app.i18n';
import { AppPreviewComponent } from './app-preview.component';

/**
 * The Preview tab, declared once and consumed twice: `BASE_APP_ROUTES` mounts it as
 * `app-definition/<id>/preview`, and `AppDefinitionContainerComponent` puts it on the descriptor so the tab bar
 * renders the link. One constant rather than two literals because the segment is what ties the link to the
 * route — a mismatch would render a tab that navigates to a URL nothing matches.
 */
export const APP_PREVIEW_TAB: EntityTabDescriptor = {
  segment: 'preview',
  i18nKey: APP_PREVIEW_I18N_KEY,
  component: AppPreviewComponent,
  testIdSuffix: 'show-preview',
};
