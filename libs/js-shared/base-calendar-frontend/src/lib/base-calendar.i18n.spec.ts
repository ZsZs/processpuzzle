import { describe, expect, it } from 'vitest';
import de from '../assets/i18n/base_calendar/de.json';
import en from '../assets/i18n/base_calendar/en.json';
import es from '../assets/i18n/base_calendar/es.json';
import fr from '../assets/i18n/base_calendar/fr.json';
import hu from '../assets/i18n/base_calendar/hu.json';
import { BASE_CALENDAR_TRANSLOCO_ALIAS, BASE_CALENDAR_TRANSLOCO_SCOPE } from './base-calendar.i18n';

/** Dotted key paths of a transloco translation file, as transloco flattens them under the scope alias. */
function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

describe('base_calendar translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = flattenKeys(en).sort();

  it('keeps the alias equal to the scope, so transloco does not camel-case it away', () => {
    expect(BASE_CALENDAR_TRANSLOCO_ALIAS).toBe(BASE_CALENDAR_TRANSLOCO_SCOPE);
  });

  it.each(Object.keys(languages))('covers every English key in %s', (language) => {
    expect(flattenKeys(languages[language as keyof typeof languages]).sort()).toEqual(englishKeys);
  });
});
