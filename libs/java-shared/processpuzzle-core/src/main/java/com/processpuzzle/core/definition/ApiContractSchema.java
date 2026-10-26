package com.processpuzzle.core.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON Schema of one component of a published OpenAPI contract, for a draft a client writes as JSON. The
 * component's {@code $ref}s are inlined, so the schema stands on its own and cannot drift from the REST contract the
 * same draft is saved through.
 */
public final class ApiContractSchema {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private ApiContractSchema() {
    }

    /**
     * @param contract  the contract's classpath resource, such as {@code base-entity-api.yaml}
     * @param component the name under {@code components/schemas}, such as {@code BaseEntityDefinitionInput}
     * @return the schema as JSON text
     * @throws IllegalStateException when the contract cannot be read or names no such component
     */
    public static String ofComponent(String contract, String component) {
        try (InputStream stream = ApiContractSchema.class.getClassLoader().getResourceAsStream(contract)) {
            if (stream == null) {
                throw new IllegalStateException("The API contract " + contract + " is not on the classpath");
            }
            JsonNode root = YAML.readTree(stream);
            JsonNode target = root.at("/components/schemas/" + component);
            if (target.isMissingNode()) {
                throw new IllegalStateException("The API contract " + contract + " has no component " + component);
            }
            return JSON.writeValueAsString(inline(JSON.convertValue(target, Map.class), root));
        } catch (IOException e) {
            throw new IllegalStateException("The API contract " + contract + " cannot be read", e);
        }
    }

    /** Replaces every {@code $ref} in the tree by the component it names. */
    private static Object inline(Object node, JsonNode contract) {
        if (node instanceof Map<?, ?> map) {
            if (map.containsKey("$ref")) {
                String pointer = ((String) map.get("$ref")).substring(1);
                return inline(JSON.convertValue(contract.at(pointer), Map.class), contract);
            }
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((name, value) -> resolved.put(String.valueOf(name), inline(value, contract)));
            return resolved;
        }
        if (node instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>();
            list.forEach(item -> resolved.add(inline(item, contract)));
            return resolved;
        }
        return node;
    }
}
