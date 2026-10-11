package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.DocumentContent;

/**
 * A locale and optional replacement content. Null means omitted, not an empty page: adding a
 * translation copies the source; updating leaves it alone. Import/create treat omission as blank.
 */
public record DocumentTranslationCommand(String locale, DocumentContent content) {

    public DocumentContent contentOrEmpty() {
        return content == null ? DocumentContent.empty() : content;
    }
}
