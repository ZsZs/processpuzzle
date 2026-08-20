package com.processpuzzle.state.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * Converts {@link StateMachineDefinition#getTransitions()} to and from a single serialized JSON
 * text column. See {@link StatesConverter} for why this uses a plain Jackson 2
 * {@code ObjectMapper} rather than Hibernate's built-in JSON format mapper, and why the column is
 * a plain portable {@code @Lob} rather than a Postgres-specific {@code jsonb}.
 */
@Converter
public class TransitionsConverter implements AttributeConverter<List<Transition>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Transition>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<Transition> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to serialize transitions to JSON", new java.io.IOException(e));
        }
    }

    @Override
    public List<Transition> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to deserialize transitions from JSON", new java.io.IOException(e));
        }
    }
}
