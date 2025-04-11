import { provideTransloco } from '@jsverse/transloco';
import { TranslocoHttpLoader } from './transloco.loader';
import { isDevMode, NgModule } from '@angular/core';

@NgModule({
  exports: [],
  providers: [
    provideTransloco({
      config: {
        availableLangs: ['en', 'de', 'hu'],
        defaultLang: 'en',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader,
    }),
  ],
})
export class LanguageSelectorModule {}
