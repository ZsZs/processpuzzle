import { provideTransloco, TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { inject, isDevMode, NgModule } from '@angular/core';
import de from './assets/i18n/widgets/de.json';
import hu from './assets/i18n/widgets/hu.json';
import en from './assets/i18n/widgets/en.json';
import es from './assets/i18n/widgets/es.json';
import { TranslocoHttpLoader } from './language-selector/transloco.loader';
import { LanguageConfig } from './language-selector/language-config';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';

@NgModule({
  imports: [TranslocoModule],
  exports: [TranslocoModule],
  providers: [
    {
      provide: provideTransloco,
      useFactory: () => {
        const runtimeConfig: LanguageConfig = inject(RUNTIME_CONFIGURATION);
        return provideTransloco({
          config: {
            availableLangs: runtimeConfig.AVAILABLE_LANGUAGES?.map((lang) => lang.code),
            defaultLang: runtimeConfig.DEFAULT_LANGUAGE,
            reRenderOnLangChange: true,
            prodMode: !isDevMode(),
          },
          loader: TranslocoHttpLoader,
        });
      },
      deps: [RUNTIME_CONFIGURATION],
    },
    provideTransloco({
      config: {
        availableLangs: ['de', 'en', 'es', 'hu'],
        defaultLang: 'en',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader,
    }),
  ],
})
export class WidgetsModule {
  constructor(private _translate: TranslocoService) {
    this._translate.setTranslation({ widgets: de }, 'de');
    this._translate.setTranslation({ widgets: hu }, 'hu');
    this._translate.setTranslation({ widgets: en }, 'en');
    this._translate.setTranslation({ widgets: es }, 'es');
  }
}
