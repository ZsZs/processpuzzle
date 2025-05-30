import { HashMap, Translation, TranslocoTestingModule, TranslocoTestingOptions } from '@jsverse/transloco';

/// <reference types="jest" />
export const mockTranslocoService = {
  load: jest.fn(),
  setActiveLang: jest.fn(),
};

export const mockLanguageConfig = {
  AVAILABLE_LANGUAGES: [
    { code: 'en', flag: 'flag-en', label: 'english' },
    { code: 'es', flag: 'flag-es', label: 'spanish' },
    { code: 'de', flag: 'flag-de', label: 'german' },
  ],
  DEFAULT_LANGUAGE: 'en',
};

export function getTranslocoTestingModule(languages: HashMap<Translation>, options: TranslocoTestingOptions = {}) {
  return TranslocoTestingModule.forRoot({
    langs: languages,
    translocoConfig: {
      availableLangs: mockLanguageConfig.AVAILABLE_LANGUAGES.map((lang) => lang.code),
      defaultLang: mockLanguageConfig.DEFAULT_LANGUAGE,
    },
    preloadLangs: true,
    ...options,
  });
}
