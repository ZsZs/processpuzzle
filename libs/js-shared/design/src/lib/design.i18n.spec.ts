import { Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import de from '../assets/i18n/design/de.json';
import en from '../assets/i18n/design/en.json';
import es from '../assets/i18n/design/es.json';
import fr from '../assets/i18n/design/fr.json';
import hu from '../assets/i18n/design/hu.json';
import { DESIGN_ROUTES } from './design.routes';

const DESIGN_TRANSLOCO_SCOPE = 'design';

/** Dotted key paths of a transloco translation file, as transloco flattens them under the scope alias. */
function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

/**
 * Every `menuTitle` the route tree declares, at any depth — the sidenav reads them off the top level and
 * the Application tab bar off the level below it, and both render the raw key when it is missing. Only
 * eagerly declared `children` are walked; a `loadChildren` branch belongs to the library that owns it.
 */
function menuTitlesOf(routes: Routes): string[] {
  return routes.flatMap((route) => {
    const menuTitle = route.data?.['menuTitle'];
    return [...(typeof menuTitle === 'string' ? [menuTitle] : []), ...menuTitlesOf(route.children ?? [])];
  });
}

describe('design translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = flattenKeys(en).sort();

  it.each(Object.keys(languages))('covers every English key in %s', (language) => {
    expect(flattenKeys(languages[language as keyof typeof languages]).sort()).toEqual(englishKeys);
  });

  it.each(Object.entries(languages))('leaves no key of %s empty', (_language, translations) => {
    const values = flattenKeys(translations).map((key) => key.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown>)[segment], translations));

    expect(values.every((value) => typeof value === 'string' && value.trim().length > 0)).toBe(true);
  });

  // The regression this exists for: `design.modules` was referenced by base-app's route for a whole
  // release without ever being added here, and the sidenav rendered the key itself.
  //
  // Filtered to the keys of *this* scope, because not every mounted branch names one. base-app's routes
  // do declare `design.*` menu titles — an inversion the tab files call out — but base-workflow's six
  // branches name keys of their own `base_workflow` scope, and holding this bundle responsible for
  // another library's key space would fail the moment any such branch is mounted here.
  it('translates every menu title the design routes declare in its own scope', () => {
    const scopePrefix = `${DESIGN_TRANSLOCO_SCOPE}.`;
    const declaredKeys = menuTitlesOf(DESIGN_ROUTES)
      .filter((menuTitle) => menuTitle.startsWith(scopePrefix))
      .map((menuTitle) => menuTitle.slice(scopePrefix.length));

    expect(declaredKeys.length).toBeGreaterThan(0);
    expect(englishKeys).toEqual(expect.arrayContaining(declaredKeys));
  });
});
