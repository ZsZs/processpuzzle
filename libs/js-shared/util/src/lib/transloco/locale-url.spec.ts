import { describe, expect, it } from 'vitest';
import { splitLocaleFromUrl, withLocale } from './locale-url';

const SUPPORTED = ['en', 'de', 'fr', 'hu', 'es'];

describe('splitLocaleFromUrl', () => {
  it('takes the locale off a prefixed path.', () => {
    expect(splitLocaleFromUrl('/hu/base-entity/samples', SUPPORTED)).toEqual({ locale: 'hu', path: '/base-entity/samples' });
  });

  it('leaves the root behind when the locale is the whole path.', () => {
    expect(splitLocaleFromUrl('/de', SUPPORTED)).toEqual({ locale: 'de', path: '/' });
    expect(splitLocaleFromUrl('/de/', SUPPORTED)).toEqual({ locale: 'de', path: '/' });
  });

  it('keeps a query string or fragment that follows the locale directly.', () => {
    expect(splitLocaleFromUrl('/fr?page=2', SUPPORTED)).toEqual({ locale: 'fr', path: '/?page=2' });
    expect(splitLocaleFromUrl('/fr#top', SUPPORTED)).toEqual({ locale: 'fr', path: '/#top' });
    expect(splitLocaleFromUrl('/fr/orders?page=2#row-3', SUPPORTED)).toEqual({ locale: 'fr', path: '/orders?page=2#row-3' });
  });

  it('accepts a URL with no leading slash, as `navigateByUrl` may hand over.', () => {
    expect(splitLocaleFromUrl('es/orders', SUPPORTED)).toEqual({ locale: 'es', path: '/orders' });
  });

  it('leaves an unprefixed URL untouched, so links made before this feature keep resolving.', () => {
    expect(splitLocaleFromUrl('/base-entity/samples', SUPPORTED)).toEqual({ path: '/base-entity/samples' });
    expect(splitLocaleFromUrl('/', SUPPORTED)).toEqual({ path: '/' });
    expect(splitLocaleFromUrl('', SUPPORTED)).toEqual({ path: '' });
  });

  it('matches a whole segment only — a path that merely starts with a language code is not a prefix.', () => {
    expect(splitLocaleFromUrl('/design/apps', SUPPORTED)).toEqual({ path: '/design/apps' });
    expect(splitLocaleFromUrl('/entity-registry', SUPPORTED)).toEqual({ path: '/entity-registry' });
  });

  it('ignores a language the application was not configured with.', () => {
    expect(splitLocaleFromUrl('/it/orders', SUPPORTED)).toEqual({ path: '/it/orders' });
  });
});

describe('withLocale', () => {
  it('prefixes a path.', () => {
    expect(withLocale('/base-entity/samples', 'hu')).toBe('/hu/base-entity/samples');
  });

  it('prefixes the root without leaving a trailing slash.', () => {
    expect(withLocale('/', 'de')).toBe('/de');
    expect(withLocale('', 'de')).toBe('/de');
  });

  it('prefixes a query-only or fragment-only URL without inserting a slash.', () => {
    expect(withLocale('/?page=2', 'fr')).toBe('/fr?page=2');
    expect(withLocale('/#top', 'fr')).toBe('/fr#top');
  });

  it('returns the path unchanged when there is no locale.', () => {
    expect(withLocale('/base-entity', undefined)).toBe('/base-entity');
  });

  it('round-trips with splitLocaleFromUrl.', () => {
    for (const path of ['/', '/base-entity/samples', '/?page=2', '/#top', '/orders?page=2#row-3']) {
      expect(splitLocaleFromUrl(withLocale(path, 'hu'), SUPPORTED)).toEqual({ locale: 'hu', path });
    }
  });
});
