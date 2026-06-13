import { TRANSLOCO_LOADER, TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoTesting, setUpTranslocoTestBed, TestTranslocoLoader, TranslocoTestConfig } from './transloco-testing.provider';
import { TranslocoTestComponent } from './transloco-test.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { screen } from '@testing-library/angular';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { Observable } from 'rxjs';

describe('transloco-testing.provider', () => {
  const config: TranslocoTestConfig = {
    scope: 'widgets',
    translations: {
      en: { TEST: 'Test' },
      de: { TEST: 'Test' },
      'widgets/en': { TEST: 'Test', OK: 'Ok', PREFIXED: { OK: 'Prefixed ok', TEST: 'Prefixed test' } },
      'widgets/de': { TEST: 'Test', OK: 'In Ordnung', PREFIXED: { OK: 'In Ordnung', TEST: 'Verschachtelt test' } },
    },
  };
  let component: TranslocoTestComponent;
  let fixture: ComponentFixture<TranslocoTestComponent>;
  let transloco: TranslocoService;

  describe('with scope: widgets', () => {
    beforeEach(async () => {
      const testVars = await setUpTranslocoTestBed(TranslocoTestComponent, config);
      fixture = testVars.fixture;
      component = testVars.component;
      transloco = testVars.translocoService;
    });

    it('should instantiate TranslocoService in the background', () => {
      expect(transloco).toBeTruthy();
    });

    it('should use the given translations', async () => {
      await transloco.load('en').toPromise?.();
      expect(transloco.translate('OK', {}, 'widgets/en')).toBe('Ok');

      await transloco.load('de').toPromise?.();
      expect(transloco.translate('OK', {}, 'widgets/de')).toBe('In Ordnung');
    });
  });

  describe('without scope', () => {
    beforeEach(async () => {
      // const result = await setUpTranslocoTestBed(TranslocoTestComponent, config);
      // transloco = result.translocoService;
      await TestBed.configureTestingModule({
        imports: [
          TranslocoTestComponent,
          TranslocoTestingModule.forRoot({
            langs: config.translations,
            translocoConfig: { availableLangs: ['en', 'de'], defaultLang: 'en' },
            preloadLangs: true,
          }),
        ],
      }).compileComponents();
      transloco = TestBed.inject(TranslocoService);
    });

    afterEach(() => {
      TestBed.resetTestingModule();
    });

    it('should instantiate TranslocoService in the background', () => {
      expect(transloco).toBeTruthy();
    });

    it('should use the given translations', async () => {
      await transloco.load('en').toPromise?.();
      expect(transloco.translate('TEST')).toBe('Test');

      await transloco.load('de').toPromise?.();
      expect(transloco.translate('TEST', {}, 'de')).toBe('Test');
    });
  });

  describe('with component', () => {
    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [
          TranslocoTestComponent,
          TranslocoTestingModule.forRoot({
            langs: config.translations,
            translocoConfig: { availableLangs: ['en', 'de'], defaultLang: 'en' },
            preloadLangs: true,
          }),
        ],
      }).compileComponents();
      transloco = TestBed.inject(TranslocoService);
      fixture = TestBed.createComponent(TranslocoTestComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    afterEach(() => {
      TestBed.resetTestingModule();
    });

    it('should render translated text', async () => {
      //      screen.debug();
      expect(component).toBeTruthy();
      expect(component.ok()).toEqual('Ok');
      expect(component.prefixedOk()).toEqual('Test');

      expect(screen.getByText('Ok', { selector: 'p' })).toBeTruthy();
      expect(screen.getByText('Test', { selector: 'p' })).toBeTruthy();
      expect(screen.getByText('Prefixed test', { selector: 'p' })).toBeTruthy();
      expect(screen.getByText('Prefixed ok', { selector: 'p' })).toBeTruthy();
    });
  });

  describe('setUpTranslocoTestBed', () => {
    it('should set active language and update the view', async () => {
      const { translocoService, setActiveLang } = await setUpTranslocoTestBed(TranslocoTestComponent, config);

      await setActiveLang('de');

      expect(translocoService.getActiveLang()).toBe('de');
      expect(screen.getByText('In Ordnung', { selector: 'p' })).toBeTruthy();
    });
  });

  describe('TestTranslocoLoader', () => {
    it('should load translations with scope', async () => {
      const testConfig: TranslocoTestConfig = {
        scope: 'widgets',
        translations: {
          en: { widgets: { TEST: 'Scoped Test' } },
        },
      };

      await TestBed.configureTestingModule({
        providers: [provideTranslocoTesting(testConfig)],
      });

      const loader = TestBed.inject(TRANSLOCO_LOADER) as TestTranslocoLoader;
      const translation = await (loader.getTranslation('en') as Observable<any>).toPromise();

      expect(translation).toEqual({ TEST: 'Scoped Test' });
    });

    it('should load translations without scope and filter out nested objects', async () => {
      const testConfig: TranslocoTestConfig = {
        translations: {
          en: {
            TEST: 'Test',
            NESTED: { SO: 'Should be filtered' },
          },
        },
      };

      await TestBed.configureTestingModule({
        providers: [provideTranslocoTesting(testConfig)],
      });

      const loader = TestBed.inject(TRANSLOCO_LOADER) as TestTranslocoLoader;
      const translation = await (loader.getTranslation('en') as Observable<any>).toPromise();

      expect(translation).toEqual({ TEST: 'Test' });
    });

    it('should load translations without scope when no nested objects exist', async () => {
      const testConfig: TranslocoTestConfig = {
        translations: {
          en: { TEST: 'Test' },
        },
      };

      await TestBed.configureTestingModule({
        providers: [provideTranslocoTesting(testConfig)],
      });

      const loader = TestBed.inject(TRANSLOCO_LOADER) as TestTranslocoLoader;
      const translation = await (loader.getTranslation('en') as Observable<any>).toPromise();

      expect(translation).toEqual({ TEST: 'Test' });
    });

    it('should return empty object if language does not exist', async () => {
      const testConfig: TranslocoTestConfig = {
        translations: {
          en: { TEST: 'Test' },
        },
      };

      await TestBed.configureTestingModule({
        providers: [provideTranslocoTesting(testConfig)],
      });

      const loader = TestBed.inject(TRANSLOCO_LOADER) as TestTranslocoLoader;
      const translation = await (loader.getTranslation('fr') as Observable<any>).toPromise();

      expect(translation).toEqual({});
    });
  });
});
