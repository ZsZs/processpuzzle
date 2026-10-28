package com.processpuzzle.app.usecase;

import java.util.List;

/** What an upload of an app's translation files stored, per language. */
public record AppTranslationUploadResult(List<Language> languages) {

    /** @param storedKeys how many keys were stored */
    public record Language(String locale, int storedKeys) {
    }
}
