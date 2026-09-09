package com.processpuzzle.document.domain;

import jakarta.persistence.Converter;

/** Persists a {@link DocumentContent} as a JSON string column. See {@link JsonColumnConverter}. */
@Converter
public class DocumentContentConverter extends JsonColumnConverter<DocumentContent> {

    public DocumentContentConverter() {
        super(DocumentContent.class);
    }
}
