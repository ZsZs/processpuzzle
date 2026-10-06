package com.processpuzzle.starter.bundle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bundle's {@code manifest.yaml}, in the shape of
 * {@code docs/business-starters/starter-manifest.example.yaml}. Only the fields the importer acts on,
 * or records, are mapped; catalog fields such as {@code category} or {@code tags} reach the catalog
 * through CI's {@code catalog.json} and are ignored here.
 *
 * <p>A starter is all or nothing: there are no dependencies between starters, no partial install and
 * no sample data, so the manifest has no field for any of them.
 *
 * @param contents     definition files by group ({@code entities}, {@code states}, ...), paths
 *                     relative to the bundle root
 * @param migration    path of the migration script inside the bundle; a placeholder until the format
 *                     is decided together with upgrades, so it is carried but never run
 * @param integrity    hashes written by CI at packaging time; optional
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StarterManifest(
        Integer manifestVersion,
        String id,
        String name,
        String version,
        String description,
        String author,
        String license,
        Integer definitionSchemaVersion,
        String icon,
        Map<String, List<String>> contents,
        String migration,
        Integrity integrity) {

    public StarterManifest {
        contents = contents == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(contents));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Integrity(String algorithm, Map<String, String> files) {

        public Integrity {
            files = files == null ? Map.of() : Map.copyOf(files);
        }
    }
}
