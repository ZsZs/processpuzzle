package com.processpuzzle.document.domain;

import jakarta.persistence.Converter;

/** Persists a {@link DocumentRoles} as a JSON string column. See {@link JsonColumnConverter}. */
@Converter
public class DocumentRolesConverter extends JsonColumnConverter<DocumentRoles> {

    public DocumentRolesConverter() {
        super(DocumentRoles.class);
    }
}
