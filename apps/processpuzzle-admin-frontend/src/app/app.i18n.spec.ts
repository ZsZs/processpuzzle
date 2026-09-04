import { describe, expect, it } from 'vitest';
import de from '../assets/i18n/de.json';
import en from '../assets/i18n/en.json';
import es from '../assets/i18n/es.json';
import fr from '../assets/i18n/fr.json';
import hu from '../assets/i18n/hu.json';
import { appRoutes } from './app.routes';

/**
 * The host application's own bundles, which nothing else validates.
 *
 * `menuTitle` in a route's `data` is a translation key, and the routes that carry it come from
 * `@processpuzzle/platform-admin` — the library declares the key, this application ships the value.
 * Transloco's answer to a missing key is the key itself, so the failure looks like a cosmetic glitch
 * ("platform.organizations" in the sidenav) rather than the missing translation it is, and no build or
 * type check sees it. The same holds for a language that falls behind English.
 */
describe('platform-admin translations', () => {
  const bundles: Record<string, Record<string, unknown>> = { de, en, es, fr, hu };

  const lookup = (bundle: Record<string, unknown>, key: string): unknown =>
    key.split('.').reduce<unknown>((value, segment) => (value === undefined || value === null ? undefined : (value as Record<string, unknown>)[segment]), bundle);

  const keysOf = (value: unknown, prefix = ''): string[] =>
    value !== null && typeof value === 'object' && !Array.isArray(value)
      ? Object.entries(value as Record<string, unknown>).flatMap(([key, nested]) => keysOf(nested, prefix ? `${prefix}.${key}` : key))
      : [prefix];

  const menuTitles = appRoutes.filter((route) => route.data?.['menuTitle']).map((route) => route.data?.['menuTitle'] as string);

  it('declares a menu title for every navigable route', () => {
    expect(menuTitles.length).toBeGreaterThan(0);
  });

  it.each(Object.keys(bundles))('%s translates every route menu title', (lang) => {
    for (const menuTitle of menuTitles) {
      expect(lookup(bundles[lang], `navigation.${menuTitle}`), `${lang}.json has no navigation.${menuTitle}`).toEqual(expect.any(String));
    }
  });

  it.each(Object.keys(bundles).filter((lang) => lang !== 'en'))('%s carries exactly the English key set', (lang) => {
    expect(keysOf(bundles[lang]).sort()).toEqual(keysOf(en).sort());
  });
});
