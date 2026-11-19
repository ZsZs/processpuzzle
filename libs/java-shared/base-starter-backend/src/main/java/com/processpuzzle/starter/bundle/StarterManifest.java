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
 * @param kind         {@code manifestVersion: 2} only: {@code starter} (the default) or {@code common}, a bundle
 *                     installed once into the organization named by its id and never into a subscriber
 * @param common       {@code manifestVersion: 2} only: the common a tenant bundle was written against
 * @param migrations   {@code manifestVersion: 2} only: migration files per upgrade step; carried, not run yet
 * @param keyPrefix    {@code manifestVersion: 3} only: what every key the starter defines starts with, followed by a
 *                     dash ({@code sailrc} for {@code sailrc-race}); required from version 3
 * @param mounts       {@code manifestVersion: 3} only: where and how the starter's modules suggest to be mounted in
 *                     the customer's application; carried, not acted on yet
 * @param integrity    hashes written by CI at packaging time; optional
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StarterManifest(
        Integer manifestVersion,
        String kind,
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
        List<String> migrations,
        Common common,
        String keyPrefix,
        List<Mount> mounts,
        Integrity integrity) {

    public static final String KIND_COMMON = "common";

    public StarterManifest {
        contents = contents == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(contents));
        migrations = migrations == null ? List.of() : List.copyOf(migrations);
        mounts = mounts == null ? List.of() : List.copyOf(mounts);
    }

    /** The manifest's version; a manifest that names none is version 1. */
    public int effectiveVersion() {
        return manifestVersion == null ? 1 : manifestVersion;
    }

    /** From version 3 a starter is a domain starter: no application, keys under {@link #keyPrefix}, mount hints. */
    public boolean isDomainStarter() {
        return effectiveVersion() >= 3;
    }

    public boolean isCommon() {
        return KIND_COMMON.equals(kind);
    }

    /** The common a tenant bundle needs: its id, and the oldest version its definitions were written against. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Common(String id, String minVersion) {
    }

    /**
     * A suggestion to mount one of the starter's modules: at {@code basePath}, with a navigation item and a launcher
     * card. Texts are keys relative to the module's transloco scope, the image a {@code module:} asset.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mount(String moduleKey, String basePath, Nav nav, Launcher launcher) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Nav(String icon, String labelKey) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Launcher(String image, String titleKey, String subtitleKey, List<String> contentKeys, String link) {

        public Launcher {
            contentKeys = contentKeys == null ? List.of() : List.copyOf(contentKeys);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Integrity(String algorithm, Map<String, String> files) {

        public Integrity {
            files = files == null ? Map.of() : Map.copyOf(files);
        }
    }
}
