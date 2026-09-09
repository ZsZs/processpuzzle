export interface LanguageDefinition {
  code: string;
  label: string;
  flag: string;
}

export interface LanguageConfig {
  DEFAULT_LANGUAGE?: string;
  AVAILABLE_LANGUAGES?: LanguageDefinition[];
}
