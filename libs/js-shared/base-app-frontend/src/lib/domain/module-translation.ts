/**
 * Frontend models of the module-translation schemas of `base-app-api.yaml`. The app is not a translation
 * editor: a user downloads the prepared files, translates off-line and uploads them again, so what the
 * frontend knows of translations is how far each language is covered and what an upload stored.
 */

/** How much of a module one language covers; the default language is complete by definition. */
export interface ModuleTranslationCoverage {
  readonly locale: string;
  readonly defaultLocale: boolean;
  readonly keyCount: number;
  readonly translatedCount: number;
  readonly missingKeys: string[];
}

/** What an upload stored for one language; the problems of content are reported, not refused. */
export interface ModuleTranslationUploadLanguage {
  readonly locale: string;
  readonly storedKeys: number;
  /** Keys of the file the module does not have; dropped. */
  readonly unknownKeys: string[];
  readonly missingKeys: string[];
  /** Stored keys whose value still equals the authored text. */
  readonly untranslatedKeys: string[];
}

export interface ModuleTranslationUploadReport {
  readonly languages: ModuleTranslationUploadLanguage[];
}

/** The stored bundle of one module, language and channel: keys relative to the module's transloco scope. */
export type ModuleTranslationBundle = Record<string, string>;
