import { TranslocoTestingModule, TranslocoTestingOptions } from '@jsverse/transloco';
import en from '../assets/i18n/widgets/en.json';
import de from '../assets/i18n/widgets/de.json';
import es from '../assets/i18n/widgets/es.json';

export function getTranslocoModule(options: TranslocoTestingOptions = {}) {
  return TranslocoTestingModule.forRoot({
    langs: { en, de, es },
    translocoConfig: {
      availableLangs: ['en', 'de', 'es'],
      defaultLang: 'en',
    },
    preloadLangs: true,
    ...options,
  });
}
