package com.processpuzzle.app.usecase;

import java.util.List;

/**
 * What an upload of translation files stored, per language. The problems of content are reported rather
 * than refused: a key the module lacks is dropped, a key the file lacks stays untranslated.
 */
public record ModuleTranslationUploadResult(List<Language> languages) {

    /**
     * @param storedKeys how many keys were stored
     * @param unknownKeys keys of the file the module does not have; dropped
     * @param missingKeys keys of the module the file does not carry
     * @param untranslatedKeys stored keys still equal to the authored text; empty for the default language
     */
    public record Language(String locale, int storedKeys, List<String> unknownKeys, List<String> missingKeys,
                           List<String> untranslatedKeys) {
    }
}
