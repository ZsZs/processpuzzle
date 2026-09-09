import { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { APP_PREVIEW_I18N_KEY } from '../base-app.i18n';
import { AppPreviewComponent } from './app-preview.component';
import { appShellRoutesGuard } from './shell/app-shell-routes';

/**
 * The Preview tab, declared once and consumed twice: `BASE_APP_ROUTES` mounts it as
 * `app-definition/<id>/preview`, and `AppDefinitionContainerComponent` puts it on the descriptor so the tab bar
 * renders the link. One constant rather than two literals because the segment is what ties the link to the
 * route — a mismatch would render a tab that navigates to a URL nothing matches.
 *
 * The tab is a *container*: `children` starts empty and {@link appShellRoutesGuard} fills it with the
 * previewed application's own routes on every navigation, so the shell's `<router-outlet>` renders real
 * screens at real URLs — `app-definition/demo/preview/orders`. The empty array is load-bearing; a route with
 * no `children` at all takes the `loadChildren` path instead, which is cached per Route object and so cannot
 * vary by `:entityId`.
 */
export const APP_PREVIEW_TAB: EntityTabDescriptor = {
  segment: 'preview',
  i18nKey: APP_PREVIEW_I18N_KEY,
  component: AppPreviewComponent,
  testIdSuffix: 'show-preview',
  children: [],
  canMatch: [appShellRoutesGuard],
};
