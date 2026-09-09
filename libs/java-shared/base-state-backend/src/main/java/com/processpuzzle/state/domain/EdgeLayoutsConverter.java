package com.processpuzzle.state.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * Converts {@link DiagramDefinition#getEdges()} to and from a single serialized JSON text column.
 * See {@link NodeLayoutsConverter} for why the {@link ObjectMapper} is an explicit Jackson 2 one.
 */
@Converter
public class EdgeLayoutsConverter implements AttributeConverter<List<EdgeLayout>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<EdgeLayout>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<EdgeLayout> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to serialize edge layouts to JSON", new java.io.IOException(e));
        }
    }

    @Override
    public List<EdgeLayout> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to deserialize edge layouts from JSON", new java.io.IOException(e));
        }
    }
}
