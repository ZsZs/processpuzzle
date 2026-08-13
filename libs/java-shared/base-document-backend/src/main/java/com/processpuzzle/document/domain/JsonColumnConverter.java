package com.processpuzzle.document.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;

/**
 * Shared base for this module's JSON-column converters. Each subclass binds one concrete value
 * type, which is the point: JPA needs the converter's target type to be reifiable, so a single
 * generic converter registered for {@code Object} would not do — the same reason
 * {@code AppGraphConverter} spells its type out.
 *
 * <p>Three deliberate choices inherited from {@code AppGraphConverter}: {@code autoApply} stays
 * off (each column opts in with {@code @Convert}, so adding a value type never silently changes
 * how an unrelated column is stored); unknown properties are ignored on read, so a column written
 * by a newer revision still loads; and the mapper is a static field rather than an injected bean,
 * because JPA instantiates converters reflectively and no container is involved.
 *
 * @param <T> the value type stored in the column
 */
abstract class JsonColumnConverter<T> implements AttributeConverter<T, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Class<T> valueType;

    protected JsonColumnConverter(Class<T> valueType) {
        this.valueType = valueType;
    }

    @Override
    public String convertToDatabaseColumn(T value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + valueType.getSimpleName(), e);
        }
    }

    @Override
    public T convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, valueType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize " + valueType.getSimpleName(), e);
        }
    }
}
