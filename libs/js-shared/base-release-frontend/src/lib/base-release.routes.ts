import { Routes } from '@angular/router';
import { ReleasePanelComponent } from './release/release-panel.component';

/**
 * The release feature's screens, to be mounted under the application's Design area — last, since a release acts
 * on everything the sections above it define. Like base-starter's, the `menuTitle` is a key of the `design`
 * scope: the designer's sidenav lists this route as a section of its own and translates only from that scope.
 * The panel registers its own `base_release` scope, whose translations the hosting application copies to
 * `assets/i18n/base_release`.
 */
export const BASE_RELEASE_ROUTES: Routes = [
  {
    path: 'release',
    title: 'ProcessPuzzle Design - Release',
    data: { icon: 'rocket_launch', menuTitle: 'design.release' },
    component: ReleasePanelComponent,
  },
];
