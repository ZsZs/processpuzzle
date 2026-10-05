package com.processpuzzle.core.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/**
 * The one canonical form a {@link DefinitionImportParticipant#fingerprints} string is written in:
 * object keys sorted, nulls omitted, no whitespace, and a caller-named set of volatile fields removed
 * at every depth. Shared so that two participants cannot disagree on what "unchanged" means.
 */
public final class CanonicalJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private CanonicalJson() {
    }

    /**
     * @param value          any Jackson-serializable value
     * @param volatileFields field names dropped wherever they occur, e.g. {@code id}, {@code version}
     */
    public static String of(Object value, Set<String> volatileFields) {
        JsonNode tree = MAPPER.valueToTree(value);
        strip(tree, volatileFields);
        try {
            // Re-read through a sorted map so that keys of nested objects are ordered too.
            Object sorted = MAPPER.treeToValue(tree, Object.class);
            return MAPPER.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to write canonical JSON", e);
        }
    }

    private static void strip(JsonNode node, Set<String> volatileFields) {
        if (node instanceof ObjectNode object) {
            object.remove(volatileFields);
            object.forEach(child -> strip(child, volatileFields));
        } else if (node.isArray()) {
            node.forEach(child -> strip(child, volatileFields));
        }
    }
}
