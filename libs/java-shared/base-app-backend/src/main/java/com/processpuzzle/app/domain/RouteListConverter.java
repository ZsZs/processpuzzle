package com.processpuzzle.app.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * Persists a {@link ModuleDefinition}'s route list as a JSON string column, on the same terms as
 * {@link AppGraphConverter}: {@code autoApply} off and attached per field, and the mapper static
 * because JPA instantiates converters reflectively rather than as Spring beans.
 *
 * <p>Unknown properties are ignored and unknown enum constants fall back to the type's
 * {@code @JsonEnumDefaultValue}, so adding a route field or a {@link RouteTarget.Kind} does not make
 * every previously persisted module unreadable.
 */
@Converter
public class RouteListConverter implements AttributeConverter<List<AppRoute>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);

    private static final TypeReference<List<AppRoute>> TYPE = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(List<AppRoute> routes) {
        if (routes == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(routes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize module routes", e);
        }
    }

    @Override
    public List<AppRoute> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize module routes", e);
        }
    }
}
