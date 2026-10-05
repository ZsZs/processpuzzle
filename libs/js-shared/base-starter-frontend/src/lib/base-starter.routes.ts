import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { BASE_STARTER_TRANSLOCO_SCOPE } from './base-starter.i18n';
import { StarterImportComponent } from './feature/starter-import.component';

/** The import screen of the feature, to be mounted under the application's Design area. */
export const BASE_STARTER_ROUTES: Routes = [
  {
    path: 'starters',
    title: 'ProcessPuzzle - Business Starters',
    data: { icon: 'inventory_2', menuTitle: 'starters' },
    component: StarterImportComponent,
    providers: [provideTranslocoScope({ scope: BASE_STARTER_TRANSLOCO_SCOPE, alias: BASE_STARTER_TRANSLOCO_SCOPE })],
  },
];
