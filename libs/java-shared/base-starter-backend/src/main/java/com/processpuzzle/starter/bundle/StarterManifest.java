package com.processpuzzle.starter.bundle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bundle's {@code manifest.yaml}, in the shape of
 * {@code docs/business-starters/starter-manifest.example.yaml}. Only the fields the importer acts on
 * are mapped; catalog fields such as {@code category} or {@code tags} are ignored here.
 *
 * @param contents     definition files by group ({@code entities}, {@code states}, ...), paths
 *                     relative to the bundle root
 * @param integrity    hashes written by CI at packaging time; optional
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StarterManifest(
        Integer manifestVersion,
        String id,
        String name,
        String version,
        String description,
        Integer definitionSchemaVersion,
        String icon,
        List<Requirement> requires,
        Map<String, List<String>> contents,
        List<String> seedData,
        Integrity integrity) {

    public StarterManifest {
        requires = requires == null ? List.of() : List.copyOf(requires);
        contents = contents == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(contents));
        seedData = seedData == null ? List.of() : List.copyOf(seedData);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Requirement(String id, String versionRange) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Integrity(String algorithm, Map<String, String> files) {

        public Integrity {
            files = files == null ? Map.of() : Map.copyOf(files);
        }
    }
}
