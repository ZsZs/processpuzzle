import { provideTransloco, TranslocoModule } from '@jsverse/transloco';
import { isDevMode, NgModule } from '@angular/core';
import { provideShareButtonsOptions } from 'ngx-sharebuttons';
import { shareIcons } from 'ngx-sharebuttons/icons';
import { TranslocoHttpLoader } from './language-selector/transloco.loader';

@NgModule({
  imports: [TranslocoModule],
  exports: [TranslocoModule],
  providers: [
    provideShareButtonsOptions(shareIcons()),
    //    provideI18Configuration(),
    provideTransloco({
      config: {
        availableLangs: ['de', 'en', 'es', 'fr', 'hu'],
        defaultLang: 'en',
        fallbackLang: ['en'],
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader,
    }),
  ],
})
export class WidgetsModule {}
