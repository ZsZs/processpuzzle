import { Routes } from '@angular/router';
import { LanguageSelectorComponent } from './language-selector/language-selector.component';
import { provideTranslocoScope } from '@jsverse/transloco';

export const widgetsRoutes: Routes = [{ path: 'anything', component: LanguageSelectorComponent, providers: [provideTranslocoScope('widgets')] }];
