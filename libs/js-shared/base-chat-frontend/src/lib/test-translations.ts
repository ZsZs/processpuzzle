import en from '../assets/i18n/base_chat/en.json';

function flatten(translations: object, prefix: string): Record<string, string> {
  return Object.fromEntries(
    Object.entries(translations).flatMap(([key, value]) =>
      typeof value === 'object' && value !== null ? Object.entries(flatten(value, `${prefix}.${key}`)) : [[`${prefix}.${key}`, String(value)]],
    ),
  );
}

/**
 * The library's real English translations, as the flat dotted keys `provideTranslocoTesting` resolves without a
 * scope — so a spec that checks what a component says also checks that the key exists. Named `test-` so the
 * library build leaves it out.
 */
export function chatTranslations(): Record<string, Record<string, string>> {
  return { en: flatten(en, 'base_chat') };
}
