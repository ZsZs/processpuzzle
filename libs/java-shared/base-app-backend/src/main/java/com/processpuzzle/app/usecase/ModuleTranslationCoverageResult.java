package com.processpuzzle.app.usecase;

import java.util.List;

/**
 * How much of a module one language covers. The default language is complete by definition — the authored
 * texts are in it — so only the others can have missing keys.
 *
 * @param translatedCount keys with a stored value that differs from the authored text
 */
public record ModuleTranslationCoverageResult(String locale, boolean defaultLocale, int keyCount,
                                              int translatedCount, List<String> missingKeys) {
}
