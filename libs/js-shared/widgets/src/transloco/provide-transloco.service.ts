import { EnvironmentProviders, isDevMode, makeEnvironmentProviders } from '@angular/core';
import { LanguageConfig } from './language-config';
import { provideTransloco } from '@jsverse/transloco';
import { TranslocoHttpLoader } from './transloco.loader';

export function provideTranslocoService(config: LanguageConfig): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideTransloco({
      config: {
        availableLangs: config.AVAILABLE_LANGUAGES?.map((lang) => lang.code) ?? [],
        defaultLang: config.DEFAULT_LANGUAGE,
        fallbackLang: ['en'],
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader,
    }),
  ]);
}
