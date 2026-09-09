package com.processpuzzle.document.domain;

import jakarta.persistence.Converter;

/** Persists a {@link DocumentPorts} as a JSON string column. See {@link JsonColumnConverter}. */
@Converter
public class DocumentPortsConverter extends JsonColumnConverter<DocumentPorts> {

    public DocumentPortsConverter() {
        super(DocumentPorts.class);
    }
}
