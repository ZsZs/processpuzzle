package com.processpuzzle.document.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists an {@link DocumentGraph} as a JSON string column. Same three deliberate choices as
 * {@code AppGraphConverter} (see that class for the full rationale): {@code autoApply} off,
 * concrete (non-generic) target type, unknown properties ignored on read. The mapper is a
 * static field, not an injected bean, because JPA instantiates converters reflectively.
 */
@Converter
public class DocumentGraphConverter implements AttributeConverter<DocumentGraph, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public String convertToDatabaseColumn(DocumentGraph graph) {
        if (graph == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(graph);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize document graph", e);
        }
    }

    @Override
    public DocumentGraph convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, DocumentGraph.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize document graph", e);
        }
    }
}
