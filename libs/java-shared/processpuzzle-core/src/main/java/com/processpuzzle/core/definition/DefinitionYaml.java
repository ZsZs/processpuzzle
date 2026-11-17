package com.processpuzzle.core.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads and narrows the definition files of a bundle, for the participants whose files are one list of definitions
 * under a top-level field, each identified by one field of its own ({@code entityDefinitions} by {@code code}).
 * An upgrade applies the definitions of a file one by one: the untouched ones are replaced, the customized ones are
 * left alone, so the participant is handed the same file narrowed to the keys it may write.
 */
public final class DefinitionYaml {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private DefinitionYaml() {
    }

    /** The keys of the definitions in {@code yaml}'s {@code listField}, in file order; an entry without one is skipped. */
    public static Set<String> keys(byte[] yaml, String listField, String keyField) {
        Set<String> keys = new LinkedHashSet<>();
        JsonNode list = read(yaml).path(listField);
        for (JsonNode entry : list) {
            JsonNode key = entry.path(keyField);
            if (key.isTextual() && !key.asText().isBlank()) {
                keys.add(key.asText());
            }
        }
        return keys;
    }

    /**
     * {@code yaml} with only the definitions whose key is in {@code keys}; every other top-level field is kept.
     * An entry without a key stays, so that the participant reports it as it would for a whole file.
     */
    public static byte[] only(byte[] yaml, String listField, String keyField, Set<String> keys) {
        JsonNode root = read(yaml);
        if (!(root instanceof ObjectNode document) || !root.path(listField).isArray()) {
            return yaml;
        }
        ArrayNode kept = YAML.createArrayNode();
        for (JsonNode entry : root.get(listField)) {
            JsonNode key = entry.path(keyField);
            if (!key.isTextual() || key.asText().isBlank() || keys.contains(key.asText())) {
                kept.add(entry);
            }
        }
        document.set(listField, kept);
        try {
            return YAML.writeValueAsBytes(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode read(byte[] yaml) {
        try {
            JsonNode root = YAML.readTree(yaml);
            return root == null ? YAML.createObjectNode() : root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
