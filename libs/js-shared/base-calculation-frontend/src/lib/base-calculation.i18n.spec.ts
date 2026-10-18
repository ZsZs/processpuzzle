import { describe, expect, it } from 'vitest';
import de from '../assets/i18n/base_calculation/de.json';
import en from '../assets/i18n/base_calculation/en.json';
import es from '../assets/i18n/base_calculation/es.json';
import fr from '../assets/i18n/base_calculation/fr.json';
import hu from '../assets/i18n/base_calculation/hu.json';
import { BASE_CALCULATION_TRANSLATION_SOURCE, BASE_CALCULATION_TRANSLOCO_SCOPE, CALCULATION_DEFINITION_I18N_SCOPE, CALCULATION_RUNS_I18N_SCOPE } from './base-calculation.i18n';
import { CALCULATION_TABS } from './feature/calculation-tabs';

function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

describe('base_calculation translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = flattenKeys(en).sort();

  it('derives the key roots from the scope the routes register', () => {
    expect(BASE_CALCULATION_TRANSLOCO_SCOPE).toBe('base_calculation');
    expect(CALCULATION_DEFINITION_I18N_SCOPE).toBe('base_calculation.calculation_definition');
    expect(CALCULATION_RUNS_I18N_SCOPE).toBe('base_calculation.runs');
  });

  it('claims its own scope', () => {
    expect(BASE_CALCULATION_TRANSLATION_SOURCE).toEqual({ scopes: ['base_calculation'], serviceRootKey: 'CALCULATION_SERVICE_ROOT', segment: 'calculation' });
  });

  it.each(Object.keys(languages))('covers every English key in %s', (language) => {
    expect(flattenKeys(languages[language as keyof typeof languages]).sort()).toEqual(englishKeys);
  });

  it.each(Object.entries(languages))('leaves no key of %s empty', (_language, translations) => {
    const values = flattenKeys(translations).map((key) => key.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown>)[segment], translations));

    expect(values.every((value) => typeof value === 'string' && value.trim().length > 0)).toBe(true);
  });

  it('labels every extra tab', () => {
    expect(englishKeys).toEqual(expect.arrayContaining(CALCULATION_TABS.map((tab) => tab.i18nKey.replace(`${BASE_CALCULATION_TRANSLOCO_SCOPE}.`, ''))));
  });
});
