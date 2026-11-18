package com.processpuzzle.app.usecase;

import java.util.List;

/**
 * What an upload of translation files stored, per language. Every key of a file is stored; what is reported is
 * how the file relates to what the module itself authors: the keys it adds (the texts of the module's widgets), the
 * ones it lacks, and the ones still equal to the authored text.
 */
public record ModuleTranslationUploadResult(List<Language> languages) {

    /**
     * @param storedKeys how many keys were stored, the additional ones included
     * @param additionalKeys keys of the file the module does not author itself — the texts its widgets name under
     *     the module's scope; stored like the rest
     * @param missingKeys keys of the module the file does not carry
     * @param untranslatedKeys stored keys still equal to the authored text; empty for the default language
     */
    public record Language(String locale, int storedKeys, List<String> additionalKeys, List<String> missingKeys,
                           List<String> untranslatedKeys) {
    }
}
