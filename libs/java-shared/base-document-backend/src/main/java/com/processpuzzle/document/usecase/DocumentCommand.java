package com.processpuzzle.document.usecase;

import java.util.List;
import java.util.Objects;

/** Candidate metadata and translations, independent of serialization and server-owned identity. */
public record DocumentCommand(DocumentProperties properties, List<DocumentTranslationCommand> translations) {

    public DocumentCommand {
        Objects.requireNonNull(properties, "properties");
        translations = translations == null ? List.of() : List.copyOf(translations);
    }
}
