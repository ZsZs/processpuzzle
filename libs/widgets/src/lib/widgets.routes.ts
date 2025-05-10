import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { LanguageSelectorComponent } from './language-selector/language-selector.component';

const loader = ['de', 'en', 'es', 'fr', 'hu'].reduce((acc: any, lang) => {
  acc[lang] = () => import(`./assets/i18n/widgets/${lang}.json`);
  return acc;
}, {});

export const widgetsRoutes: Routes = [{ path: 'anything', component: LanguageSelectorComponent, providers: [provideTranslocoScope({ scope: 'widgets', loader })] }];
