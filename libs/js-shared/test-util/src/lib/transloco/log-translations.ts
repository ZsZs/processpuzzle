import { TranslocoService } from '@jsverse/transloco';

export function logTranslations(transloco: TranslocoService): void {
  // Current active language
  const lang = transloco.getActiveLang();
  console.log('Active language:', lang);

  // Full translation map for the active language
  const translations = transloco.getTranslation(lang);
  console.log('Translations:', translations);

  // All loaded translations (all languages)
  const all = transloco.getTranslation();
  console.log('All loaded translations:', all);
}
