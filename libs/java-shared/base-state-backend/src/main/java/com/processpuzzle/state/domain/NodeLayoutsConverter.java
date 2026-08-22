package com.processpuzzle.state.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * Converts {@link DiagramDefinition#getNodes()} to and from a single serialized JSON text column.
 *
 * <p>A plain Jackson 2 {@link ObjectMapper} for the same reason {@link StatesConverter} uses one:
 * Spring Boot 4 reads and writes HTTP with Jackson 3, so which Jackson version Hibernate's
 * auto-detected format mapper would bind to is not obvious, and this way it is explicit.
 */
@Converter
public class NodeLayoutsConverter implements AttributeConverter<List<NodeLayout>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<NodeLayout>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<NodeLayout> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to serialize node layouts to JSON", new java.io.IOException(e));
        }
    }

    @Override
    public List<NodeLayout> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to deserialize node layouts from JSON", new java.io.IOException(e));
        }
    }
}
