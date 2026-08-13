package com.processpuzzle.widget.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * Persists a {@link Port} list as a JSON string column. Attached explicitly to both
 * {@code inputPorts} and {@code outputPorts}; see {@link JsonMapConverter} for why {@code autoApply}
 * stays off.
 *
 * <p>Unknown properties are ignored and unknown enum constants fall back to the type's
 * {@code @JsonEnumDefaultValue}, so adding or removing a port field does not make every previously
 * persisted row unreadable.
 */
@Converter
public class PortListConverter implements AttributeConverter<List<Port>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);

    private static final TypeReference<List<Port>> TYPE = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(List<Port> ports) {
        if (ports == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(ports);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize widget ports", e);
        }
    }

    @Override
    public List<Port> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize widget ports", e);
        }
    }
}
