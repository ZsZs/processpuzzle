package com.processpuzzle.calculation.domain.definition;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

/**
 * One parameter of a calculation: an entity parameter, whose value is an object id and which the calculation sees
 * as that object's payload, or a plain one of a {@link #type}.
 *
 * @param entity       the entity definition code of an entity parameter
 * @param type         a plain parameter's type, one of {@link #TYPES}
 * @param defaultValue used when a caller supplies no value
 */
public record ParameterSpec(String entity, String type, @JsonProperty("default") Object defaultValue) {

    public static final Set<String> TYPES = Set.of("string", "number", "boolean", "date", "date-time");

    public boolean isEntity() {
        return entity != null && !entity.isBlank();
    }
}
