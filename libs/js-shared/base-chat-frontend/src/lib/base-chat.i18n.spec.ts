import { describe, expect, it } from 'vitest';
import de from '../assets/i18n/base_chat/de.json';
import en from '../assets/i18n/base_chat/en.json';
import es from '../assets/i18n/base_chat/es.json';
import fr from '../assets/i18n/base_chat/fr.json';
import hu from '../assets/i18n/base_chat/hu.json';
import { BASE_CHAT_TRANSLOCO_SCOPE } from './base-chat.i18n';

function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

describe('base_chat translations', () => {
  const languages = { de, en, es, fr, hu };
  const englishKeys = flattenKeys(en).sort();

  it('names the scope the translation files are published under', () => {
    expect(BASE_CHAT_TRANSLOCO_SCOPE).toBe('base_chat');
  });

  it.each(Object.keys(languages))('covers every English key in %s', (language) => {
    expect(flattenKeys(languages[language as keyof typeof languages]).sort()).toEqual(englishKeys);
  });

  it.each(Object.entries(languages))('leaves no key of %s empty', (_language, translations) => {
    const values = flattenKeys(translations).map((key) => key.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown>)[segment], translations));

    expect(values.every((value) => typeof value === 'string' && value.trim().length > 0)).toBe(true);
  });
});
