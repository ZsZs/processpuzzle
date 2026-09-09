package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;

import java.util.List;

/**
 * A document as the detail endpoints return it: the invariant record, the one locale's content that
 * was asked for, and the publication state of every locale.
 *
 * @param selected the requested locale's content, or {@code null} when that locale has no
 *                 translation — the document itself still exists and is still worth returning, so
 *                 this is not an error. A caller that needs to distinguish asks for the translation
 *                 directly.
 * @param states   every locale, content-free. Always populated, so a client can render the locale
 *                 selector from the same response that gave it the content.
 */
public record DocumentDetails(Document document, DocumentTranslationView selected, List<DocumentTranslationView> states) {

    public DocumentDetails {
        states = states == null ? List.of() : List.copyOf(states);
    }
}
