import '../../test-setup';
import { TranslocoService } from '@jsverse/transloco';
import { setUpTranslocoTestBed, TranslocoTestConfig } from './transloco-testing.provider';
import { TranslocoTestComponent } from './transloco-test.component';
import { screen } from '@testing-library/angular';

describe('transloco-testing.provider', () => {
  const config: TranslocoTestConfig = {
    scope: 'widgets',
    translations: {
      en: { TEST: 'Test', widgets: { TEST: 'Test', OK: 'Ok' } },
      de: { TEST: 'Test', widgets: { TEST: 'Test', OK: 'In Ordnung' } },
    },
  };
  let component: TranslocoTestComponent;
  let transloco: TranslocoService;

  describe('with scope: widgets', () => {
    beforeEach(async () => {
      const result = await setUpTranslocoTestBed(TranslocoTestComponent, config);
      transloco = result.translocoService;
    });

    it('should instantiate TranslocoService in the background', () => {
      expect(transloco).toBeTruthy();
    });

    it('should use the given translations', async () => {
      await transloco.load('en').toPromise?.();
      expect(transloco.translate('OK')).toBe('Ok');

      await transloco.load('de').toPromise?.();
      expect(transloco.translate('OK', {}, 'de')).toBe('In Ordnung');
    });
  });

  describe('without scope', () => {
    beforeEach(async () => {
      const result = await setUpTranslocoTestBed(TranslocoTestComponent, config);
      transloco = result.translocoService;
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
      const result = await setUpTranslocoTestBed(TranslocoTestComponent, config);
      component = result.component;
      transloco = result.translocoService;
    });

    it('should render translated text', async () => {
      expect(component).toBeTruthy();
      expect(screen.getByText('Test')).toBeInTheDocument();
    });
  });
});
