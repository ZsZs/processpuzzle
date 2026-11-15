import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
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

  it('has every key a component or a widget registration names', () => {
    const named = new Set<string>();
    const walk = (directory: string) => {
      for (const entry of readdirSync(directory)) {
        const path = join(directory, entry);
        if (statSync(path).isDirectory()) {
          walk(path);
        } else if (entry.endsWith('.ts') && !entry.endsWith('.spec.ts')) {
          for (const match of readFileSync(path, 'utf-8').matchAll(/'(base_chat\.[a-z_.]+)'/g)) named.add(match[1] as string);
        }
      }
    };
    // The tests run from a bundle, where __dirname is not this directory: start from the workspace root.
    walk(resolve(process.cwd(), 'libs/js-shared/base-chat-frontend/src/lib'));

    const have = new Set(englishKeys.map((key) => `${BASE_CHAT_TRANSLOCO_SCOPE}.${key}`));
    expect(named.size).toBeGreaterThan(10);
    expect([...named].filter((key) => !have.has(key))).toEqual([]);
  });
});
