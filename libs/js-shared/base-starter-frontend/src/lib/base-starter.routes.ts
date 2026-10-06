import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { BASE_STARTER_TRANSLOCO_SCOPE } from './base-starter.i18n';
import { StarterImportComponent } from './feature/starter-import.component';

/**
 * The import screen of the feature, to be mounted under the application's Design area. Like base-rule's,
 * the `menuTitle` is a key of the `design` scope: the designer's sidenav lists this route as a section of
 * its own and translates only from that scope.
 */
export const BASE_STARTER_ROUTES: Routes = [
  {
    path: 'starters',
    title: 'ProcessPuzzle - Business Starters',
    data: { icon: 'inventory_2', menuTitle: 'design.starters' },
    component: StarterImportComponent,
    providers: [provideTranslocoScope({ scope: BASE_STARTER_TRANSLOCO_SCOPE, alias: BASE_STARTER_TRANSLOCO_SCOPE })],
  },
];
