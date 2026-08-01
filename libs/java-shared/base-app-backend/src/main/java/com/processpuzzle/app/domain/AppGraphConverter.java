package com.processpuzzle.app.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists an {@link AppGraph} as a JSON string column.
 *
 * <p>Three deliberate choices:
 *
 * <ul>
 *   <li><b>{@code autoApply} is off.</b> Converters must be attached per field with
 *       {@code @Convert}. The whole application runs one persistence unit scanning
 *       {@code com.processpuzzle}, so an auto-applied converter is visible to every entity in
 *       every ProcessPuzzle library — a needless hazard.
 *   <li><b>The target type is concrete,</b> not {@code List<Region>} or similar. Hibernate
 *       resolves a converter's attribute type from the generic superinterface, and a converter
 *       class that is itself generic cannot be resolved at all.
 *       <li><b>Unknown properties are ignored on read.</b> Removing a field from one of the graph
 *       records must not make every previously persisted blob unreadable.
 * </ul>
 *
 * <p>The mapper is a static field rather than an injected bean because JPA instantiates
 * converters reflectively through a no-arg constructor — they are not Spring beans. This also
 * matches how {@code ImportRules} and {@code ExportRules} obtain their mappers.
 */
@Converter
public class AppGraphConverter implements AttributeConverter<AppGraph, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public String convertToDatabaseColumn(AppGraph graph) {
        if (graph == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(graph);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize app graph", e);
        }
    }

    @Override
    public AppGraph convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, AppGraph.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize app graph", e);
        }
    }
}
