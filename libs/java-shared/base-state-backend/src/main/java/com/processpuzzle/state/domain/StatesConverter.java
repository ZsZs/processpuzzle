package com.processpuzzle.state.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * Converts {@link StateMachineDefinition#getStates()} to and from a single serialized JSON text
 * column.
 *
 * <p>A plain Jackson 2 {@link ObjectMapper} rather than Hibernate's built-in
 * {@code @JdbcTypeCode(SqlTypes.JSON)} format mapper: Spring Boot 4 reads and writes HTTP with
 * Jackson 3 ({@code tools.jackson}, see {@code base-document-api.yaml}'s note on
 * {@code TiptapDocument}), so which Jackson version Hibernate's auto-detected format mapper would
 * bind to is not obvious, and this way it is explicit and identical to the Jackson 2
 * {@code ObjectMapper} already used for YAML import/export in {@code ImportRules}/{@code
 * ExportRules}. See {@link StateMachineDefinition}'s class javadoc for why the column is a plain
 * portable {@code @Lob} rather than a Postgres-specific {@code jsonb}.
 */
@Converter
public class StatesConverter implements AttributeConverter<List<State>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<State>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<State> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to serialize states to JSON", new java.io.IOException(e));
        }
    }

    @Override
    public List<State> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to deserialize states from JSON", new java.io.IOException(e));
        }
    }
}
