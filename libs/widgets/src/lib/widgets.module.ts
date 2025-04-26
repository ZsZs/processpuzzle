import { provideTransloco, TranslocoModule } from '@jsverse/transloco';
import { inject, isDevMode, NgModule } from '@angular/core';
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
export class WidgetsModule {}
