import { inject, InjectionToken, isDevMode, Provider } from '@angular/core';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { provideTranslocoConfig, TRANSLOCO_CONFIG } from '@jsverse/transloco';
import { LanguageConfig } from './language-config';

const I18N_CONFIG = new InjectionToken<LanguageConfig>('LANGUAGE_CONFIG');

export function provideI18Configuration(): Provider[] {
  return [
    {
      provide: I18N_CONFIG,
      useFactory: () => {
        const runtimeConfig = inject(RUNTIME_CONFIGURATION) as LanguageConfig;

        return {
          availableLangs: runtimeConfig.AVAILABLE_LANGUAGES?.map((lang) => lang.code),
          defaultLang: runtimeConfig.DEFAULT_LANGUAGE,
        };
      },
      deps: [RUNTIME_CONFIGURATION], // declare the dependency
    },
    {
      provide: TRANSLOCO_CONFIG,
      useFactory: () => {
        const config = inject(I18N_CONFIG);
        return provideTranslocoConfig({
          ...config,
          reRenderOnLangChange: true,
          prodMode: !isDevMode(),
        });
      },
      deps: [I18N_CONFIG],
    },
  ];
}
