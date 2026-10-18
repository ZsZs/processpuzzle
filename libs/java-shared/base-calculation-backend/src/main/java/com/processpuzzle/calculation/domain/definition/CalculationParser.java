package com.processpuzzle.calculation.domain.definition;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads a calculation's YAML source into a {@link CalculationModel}. An unknown key is an error rather than
 * ignored — in a hand-written definition it is almost always a typo — and every error names where it is.
 */
public final class CalculationParser {

    private static final Pattern DOCUMENT_SEPARATOR = Pattern.compile("(?m)^---[ \\t]*$\\R?");

    private static final ObjectMapper YAML = JsonMapper.builder(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();

    /** The model, or the reason the source cannot be read as one. */
    public record Parsed(CalculationModel model, String error) {

        public boolean isParsed() {
            return model != null;
        }
    }

    private CalculationParser() {
    }

    public static Parsed parse(String source) {
        if (source == null || !hasContent(source)) {
            return new Parsed(null, "the source is empty");
        }
        try {
            CalculationModel model = YAML.readValue(source, CalculationModel.class);
            return model == null ? new Parsed(null, "the source is empty") : new Parsed(model, null);
        } catch (JsonMappingException e) {
            return new Parsed(null, describe(e));
        } catch (JsonProcessingException e) {
            return new Parsed(null, "not valid YAML" + at(e.getLocation()) + ": " + e.getOriginalMessage());
        }
    }

    /** The source as a plain map, as stored next to it. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(String source) {
        try {
            return YAML.readValue(source, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getOriginalMessage(), e);
        }
    }

    /** A file of several calculations, one per YAML document, split into each document's own source text. */
    public static List<String> documents(String file) {
        List<String> documents = new ArrayList<>();
        for (String document : DOCUMENT_SEPARATOR.split(file == null ? "" : file)) {
            if (hasContent(document)) {
                documents.add(document.strip() + System.lineSeparator());
            }
        }
        return documents;
    }

    private static boolean hasContent(String text) {
        return text.lines().anyMatch(line -> !line.isBlank() && !line.trim().startsWith("#"));
    }

    private static String describe(JsonMappingException e) {
        String path = e.getPath().stream()
                .map(reference -> reference.getFieldName() != null ? reference.getFieldName() : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining(".")).replace(".[", "[");
        String message = e.getOriginalMessage();
        int detail = message.indexOf(" (class ");
        if (message.startsWith("Unrecognized field") && detail > 0) {
            message = message.substring(0, detail).replace("Unrecognized field", "unknown key");
        }
        return (path.isEmpty() ? "" : "'" + path + "': ") + message + at(e.getLocation());
    }

    private static String at(JsonLocation location) {
        return location == null || location.getLineNr() < 0 ? "" : " (line %d, column %d)".formatted(location.getLineNr(),
                location.getColumnNr());
    }
}
