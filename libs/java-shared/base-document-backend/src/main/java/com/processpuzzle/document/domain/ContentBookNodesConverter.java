package com.processpuzzle.document.domain;

import jakarta.persistence.Converter;

/** Persists a {@link ContentBookNodes} as a JSON string column. See {@link JsonColumnConverter}. */
@Converter
public class ContentBookNodesConverter extends JsonColumnConverter<ContentBookNodes> {

    public ContentBookNodesConverter() {
        super(ContentBookNodes.class);
    }
}
