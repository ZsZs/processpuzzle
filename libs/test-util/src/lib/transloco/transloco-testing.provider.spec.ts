import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { setUpTranslocoTestBed, TranslocoTestConfig } from './transloco-testing.provider';
import { TranslocoTestComponent } from './transloco-test.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { screen } from '@testing-library/angular';
import { beforeEach, describe, expect, it } from 'vitest';

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
});
