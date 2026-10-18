package com.processpuzzle.calculation.domain.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a run's results land. Either one object — the entity parameter {@code target} names, by default the one
 * parameter of the output entity — or, with {@code for} naming a grouping attribute, one object per group, found
 * or created by the {@code key} attributes.
 *
 * @param mapping         target attribute → the decision, group column or parameter its value comes from
 * @param materialization {@code stored} (the default) writes on every run; {@code onRead} computes on request only
 */
public record OutputSpec(
        String entity,
        String target,
        @JsonProperty("for") String scope,
        List<String> key,
        Map<String, String> mapping,
        String materialization) {

    public static final String STORED = "stored";
    public static final String ON_READ = "onRead";

    public OutputSpec {
        key = key == null ? List.of() : List.copyOf(key);
        mapping = mapping == null ? new LinkedHashMap<>() : mapping;
        materialization = materialization == null ? STORED : materialization;
    }

    @JsonIgnore
    public boolean isStored() {
        return STORED.equals(materialization);
    }

    @JsonIgnore
    public boolean isGrouped() {
        return scope != null && !scope.isBlank();
    }
}
