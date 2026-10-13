import { describe, expect, it } from 'vitest';
import de from '../assets/i18n/base_release/de.json';
import en from '../assets/i18n/base_release/en.json';
import es from '../assets/i18n/base_release/es.json';
import fr from '../assets/i18n/base_release/fr.json';
import hu from '../assets/i18n/base_release/hu.json';
import { BASE_RELEASE_TRANSLATION_SOURCE, BASE_RELEASE_TRANSLOCO_SCOPE } from './base-release.i18n';

function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

describe('base_release translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = flattenKeys(en).sort();

  it('claims its own scope and names the backend root that would serve it', () => {
    expect(BASE_RELEASE_TRANSLOCO_SCOPE).toBe('base_release');
    expect(BASE_RELEASE_TRANSLATION_SOURCE).toEqual({ scopes: ['base_release'], serviceRootKey: 'RELEASE_SERVICE_ROOT', segment: 'release' });
  });

  it.each(Object.keys(languages))('covers every English key in %s', (language) => {
    expect(flattenKeys(languages[language as keyof typeof languages]).sort()).toEqual(englishKeys);
  });
});
