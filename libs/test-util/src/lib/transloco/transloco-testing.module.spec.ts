import { HashMap, Translation, TranslocoTestingModule } from '@jsverse/transloco';
import { getTranslocoTestingModule, mockLanguageConfig, mockTranslocoService } from './transloco-testing.module';

describe('transloco-testing.module', () => {
  describe('mockTranslocoService', () => {
    it('should have load method', () => {
      expect(mockTranslocoService.load).toBeDefined();
      expect(typeof mockTranslocoService.load).toBe('function');
    });

    it('should have setActiveLang method', () => {
      expect(mockTranslocoService.setActiveLang).toBeDefined();
      expect(typeof mockTranslocoService.setActiveLang).toBe('function');
    });
  });

  describe('mockLanguageConfig', () => {
    it('should have AVAILABLE_LANGUAGES array', () => {
      expect(mockLanguageConfig.AVAILABLE_LANGUAGES).toBeDefined();
      expect(Array.isArray(mockLanguageConfig.AVAILABLE_LANGUAGES)).toBe(true);
    });

    it('should have 3 languages defined', () => {
      expect(mockLanguageConfig.AVAILABLE_LANGUAGES.length).toBe(3);
    });

    it('should have english as first language', () => {
      expect(mockLanguageConfig.AVAILABLE_LANGUAGES[0]).toEqual({
        code: 'en',
        flag: 'flag-en',
        label: 'english',
      });
    });

    it('should have spanish as second language', () => {
      expect(mockLanguageConfig.AVAILABLE_LANGUAGES[1]).toEqual({
        code: 'es',
        flag: 'flag-es',
        label: 'spanish',
      });
    });

    it('should have german as third language', () => {
      expect(mockLanguageConfig.AVAILABLE_LANGUAGES[2]).toEqual({
        code: 'de',
        flag: 'flag-de',
        label: 'german',
      });
    });

    it('should have DEFAULT_LANGUAGE set to en', () => {
      expect(mockLanguageConfig.DEFAULT_LANGUAGE).toBe('en');
    });
  });

  describe('getTranslocoModule', () => {
    let mockLanguages: HashMap<Translation>;

    beforeEach(() => {
      mockLanguages = {
        en: { TEST: 'Test' },
        es: { TEST: 'Prueba' },
      };
    });

    it('should return TranslocoTestingModule', () => {
      const spy = jest.spyOn(TranslocoTestingModule, 'forRoot');

      const result = getTranslocoTestingModule(mockLanguages);

      expect(spy).toHaveBeenCalled();
      expect(result).toBeDefined();

      spy.mockRestore();
    });

    it('should configure module with provided languages', () => {
      const spy = jest.spyOn(TranslocoTestingModule, 'forRoot');

      getTranslocoTestingModule(mockLanguages);

      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({
          langs: mockLanguages,
        }),
      );

      spy.mockRestore();
    });

    it('should configure module with language codes from mockLanguageConfig', () => {
      const spy = jest.spyOn(TranslocoTestingModule, 'forRoot');

      getTranslocoTestingModule(mockLanguages);

      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({
          translocoConfig: expect.objectContaining({
            availableLangs: ['en', 'es', 'de'],
          }),
        }),
      );

      spy.mockRestore();
    });

    it('should configure module with default language from mockLanguageConfig', () => {
      const spy = jest.spyOn(TranslocoTestingModule, 'forRoot');

      getTranslocoTestingModule(mockLanguages);

      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({
          translocoConfig: expect.objectContaining({
            defaultLang: 'en',
          }),
        }),
      );

      spy.mockRestore();
    });

    it('should set preloadLangs to true by default', () => {
      const spy = jest.spyOn(TranslocoTestingModule, 'forRoot');

      getTranslocoTestingModule(mockLanguages);

      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({
          preloadLangs: true,
        }),
      );

      spy.mockRestore();
    });

    it('should override default options with provided options', () => {
      const spy = jest.spyOn(TranslocoTestingModule, 'forRoot');
      const customOptions = { preloadLangs: false, fallbackLang: 'es' };

      getTranslocoTestingModule(mockLanguages, customOptions);

      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({
          preloadLangs: false,
          fallbackLang: 'es',
        }),
      );

      spy.mockRestore();
    });
  });
});
