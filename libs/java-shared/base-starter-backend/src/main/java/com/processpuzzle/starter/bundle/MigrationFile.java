package com.processpuzzle.starter.bundle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.core.definition.MigrationStep;
import com.processpuzzle.starter.registry.StarterVersions;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A bundle's {@code migrations/<from>-to-<to>.yaml}: how the objects of version {@code from} must change to fit the
 * definitions of version {@code to}. The steps are the release machinery's own — an operation on the objects of one
 * definition, which the feature that stores them runs when the organization releases the upgrade:
 *
 * <pre>
 * migration:
 *   from: 1.0.0
 *   to: 1.1.0
 *   steps:
 *     - kind: entity
 *       key: customer
 *       operation: renameAttribute
 *       subject: customerName
 *       parameters: { to: name }
 * </pre>
 *
 * @param steps in the order they run
 */
public record MigrationFile(String from, String to, List<MigrationStep> steps) {

    private static final String DELIMITER = "-to-";
    private static final String DIRECTORY = "migrations/";
    private static final String EXTENSION = ".yaml";

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public MigrationFile {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** The path of the migration file from {@code from} to {@code to}. */
    public static String pathOf(String from, String to) {
        return DIRECTORY + from + DELIMITER + to + EXTENSION;
    }

    /** The two versions a migration path names, or empty when it is not {@code migrations/<from>-to-<to>.yaml}. */
    public static Optional<String[]> versionsOf(String path) {
        if (path == null || !path.startsWith(DIRECTORY) || !path.endsWith(EXTENSION) || path.indexOf('/', DIRECTORY.length()) >= 0) {
            return Optional.empty();
        }
        String stem = path.substring(DIRECTORY.length(), path.length() - EXTENSION.length());
        int at = stem.indexOf(DELIMITER);
        if (at <= 0 || at + DELIMITER.length() >= stem.length()) {
            return Optional.empty();
        }
        return Optional.of(new String[] {stem.substring(0, at), stem.substring(at + DELIMITER.length())});
    }

    /**
     * Reads the file at {@code path}.
     *
     * @throws IllegalArgumentException with what is wrong, when the name is not a migration's, the YAML is not readable,
     *                                  the versions disagree with the name or are not in order, or a step is incomplete
     */
    public static MigrationFile read(String path, byte[] yaml) {
        String[] named = versionsOf(path).orElseThrow(() -> new IllegalArgumentException(
                "a migration file is named migrations/<from>-to-<to>.yaml, not '" + path + "'"));
        Document document;
        try {
            document = YAML.readValue(yaml, Document.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("not a readable migration: " + e.getMessage(), e);
        }
        Body body = document == null ? null : document.migration();
        if (body == null) {
            throw new IllegalArgumentException("a migration file needs a 'migration:' block");
        }
        if (!named[0].equals(body.from()) || !named[1].equals(body.to())) {
            throw new IllegalArgumentException("the file is named for " + named[0] + " → " + named[1]
                    + " but says " + body.from() + " → " + body.to());
        }
        if (StarterVersions.compare(body.from(), body.to()) >= 0) {
            throw new IllegalArgumentException("a migration goes to a newer version: " + body.from() + " → " + body.to());
        }
        List<MigrationStep> steps = new java.util.ArrayList<>();
        List<Entry> entries = body.steps() == null ? List.of() : body.steps();
        for (int index = 0; index < entries.size(); index++) {
            steps.add(entries.get(index).toStep(index + 1));
        }
        return new MigrationFile(body.from(), body.to(), steps);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Document(Body migration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Body(String from, String to, List<Entry> steps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Entry(String id, String kind, String key, String operation, String subject, Map<String, Object> parameters) {

        MigrationStep toStep(int number) {
            if (isBlank(kind) || isBlank(key) || isBlank(operation)) {
                throw new IllegalArgumentException("step " + number + " needs a kind, a key and an operation");
            }
            return new MigrationStep(isBlank(id) ? "step-" + number : id, kind, key, operation, subject, parameters);
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
