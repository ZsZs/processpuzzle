package com.processpuzzle.starter.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;

/**
 * The registry's {@code catalog.json}, as CI writes it next to the bundles: every starter, with every
 * version ever published, yanked ones included. Unknown fields are ignored so CI can add catalog
 * fields — screenshots, a changelog — without a backend release.
 *
 * @param catalogVersion version of this file's format
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StarterCatalog(Integer catalogVersion, List<Starter> starters) {

    public static final StarterCatalog EMPTY = new StarterCatalog(1, List.of());

    public StarterCatalog {
        starters = starters == null ? List.of() : List.copyOf(starters);
    }

    public Optional<Starter> starter(String id) {
        return starters.stream().filter(starter -> starter.id() != null && starter.id().equals(id)).findFirst();
    }

    /** A starter as its newest manifest names it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Starter(String id, String name, String description, String author, String license,
                          String category, List<String> tags, String icon, List<Version> versions) {

        public Starter {
            tags = tags == null ? List.of() : List.copyOf(tags);
            versions = versions == null ? List.of() : List.copyOf(versions);
        }

        public Optional<Version> version(String version) {
            return versions.stream().filter(candidate -> candidate.version() != null && candidate.version().equals(version)).findFirst();
        }
    }

    /**
     * One published version.
     *
     * @param publishedAt ISO-8601 instant, or a bare date
     * @param status      {@code published}, {@code deprecated} or {@code yanked}
     * @param sha256      of {@code bundle.zip}; the install refuses a download that does not match it
     * @param bundle      path of the bundle, relative to the registry's base URL
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Version(String version, String publishedAt, String status, Integer definitionSchemaVersion,
                          String sha256, String bundle) {

        public static final String PUBLISHED = "published";
        public static final String DEPRECATED = "deprecated";
        public static final String YANKED = "yanked";

        /** A missing status reads as published: the field came after the first catalogs. */
        public String effectiveStatus() {
            return status == null || status.isBlank() ? PUBLISHED : status;
        }
    }
}
