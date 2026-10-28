/**
 * Frontend models of the app-translation schemas of `base-app-api.yaml`. As for a module, the app is not a translation
 * editor: a user downloads the files, translates off-line and uploads them again.
 */

/** What an upload stored for one language. */
export interface AppTranslationUploadLanguage {
  readonly locale: string;
  readonly storedKeys: number;
}

export interface AppTranslationUploadReport {
  readonly languages: AppTranslationUploadLanguage[];
}

/** The stored bundle of one application, language and channel: flat keys, whatever the application's widgets name. */
export type AppTranslationBundle = Record<string, string>;
