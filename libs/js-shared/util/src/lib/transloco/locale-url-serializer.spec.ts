import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { AvailableLangs, TranslocoService } from '@jsverse/transloco';
import { LocaleUrlSerializer } from './locale-url-serializer';

/**
 * Only the three members the serializer touches. A real `TranslocoService` would need a loader, a
 * transpiler and an HTTP backend to answer `getAvailableLangs()`, none of which says anything about
 * URL handling.
 */
class FakeTranslocoService {
  activeLang = 'en';
  availableLangs: AvailableLangs = ['en', 'de', 'fr', 'hu', 'es'];

  getActiveLang(): string {
    return this.activeLang;
  }

  setActiveLang(lang: string): void {
    this.activeLang = lang;
  }

  getAvailableLangs(): AvailableLangs {
    return this.availableLangs;
  }
}

describe('LocaleUrlSerializer', () => {
  let serializer: LocaleUrlSerializer;
  let transloco: FakeTranslocoService;

  beforeEach(() => {
    transloco = new FakeTranslocoService();
    TestBed.configureTestingModule({
      providers: [LocaleUrlSerializer, { provide: TranslocoService, useValue: transloco }],
    });
    serializer = TestBed.inject(LocaleUrlSerializer);
  });

  describe('parse', () => {
    it('hides the locale from the router, which sees the prefix-free URL.', () => {
      const tree = serializer.parse('/hu/base-entity/samples');

      expect(tree.root.children['primary'].segments.map((segment) => segment.path)).toEqual(['base-entity', 'samples']);
    });

    it('switches the active language to the one the URL names, so a deep link reproduces it.', () => {
      serializer.parse('/hu/base-entity');

      expect(transloco.getActiveLang()).toBe('hu');
    });

    it('leaves the active language alone when the URL names no locale.', () => {
      transloco.setActiveLang('de');

      serializer.parse('/base-entity');

      expect(transloco.getActiveLang()).toBe('de');
    });

    it('leaves the active language alone when the URL names a language the application lacks.', () => {
      const tree = serializer.parse('/it/base-entity');

      expect(transloco.getActiveLang()).toBe('en');
      expect(tree.root.children['primary'].segments.map((segment) => segment.path)).toEqual(['it', 'base-entity']);
    });

    it('keeps query parameters and the fragment.', () => {
      const tree = serializer.parse('/fr/orders?page=2#row-3');

      expect(tree.queryParams).toEqual({ page: '2' });
      expect(tree.fragment).toBe('row-3');
      expect(transloco.getActiveLang()).toBe('fr');
    });
  });

  describe('serialize', () => {
    it('prefixes the active language.', () => {
      transloco.setActiveLang('hu');

      expect(serializer.serialize(serializer.parse('/base-entity/samples'))).toBe('/hu/base-entity/samples');
    });

    it('prefixes the default language too, so a page has one canonical URL per language.', () => {
      expect(serializer.serialize(serializer.parse('/base-entity'))).toBe('/en/base-entity');
    });

    it('prefixes the root without a trailing slash.', () => {
      transloco.setActiveLang('de');

      expect(serializer.serialize(serializer.parse('/'))).toBe('/de');
    });

    it('round-trips a URL that already carries a prefix rather than doubling it.', () => {
      expect(serializer.serialize(serializer.parse('/hu/base-entity/samples'))).toBe('/hu/base-entity/samples');
    });

    it('re-serializes an unprefixed legacy URL under the active language.', () => {
      transloco.setActiveLang('es');

      expect(serializer.serialize(serializer.parse('/design/apps'))).toBe('/es/design/apps');
    });
  });
});
