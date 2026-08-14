package com.processpuzzle.widget.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

/**
 * Persists {@code WidgetDefinition.propsSchema} as a JSON string column.
 *
 * <p>{@code autoApply} is off, as in base-app's AppGraphConverter: the application runs one
 * persistence unit scanning {@code com.processpuzzle}, so an auto-applied converter for
 * {@code Map<String, Object>} would attach itself to every such field in every ProcessPuzzle
 * library. Converters are attached per field with {@code @Convert}.
 *
 * <p>Null round-trips as null rather than as {@code {}} — "no schema declared" is a distinct state
 * from "declares an empty schema", which the contract also preserves by making the field nullable.
 *
 * <p>The mapper is static because JPA instantiates converters reflectively through a no-arg
 * constructor; they are not Spring beans.
 */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize widget props schema", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize widget props schema", e);
        }
    }
}
