package com.processpuzzle.core.i18n;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Reads the bundled {@code default-translations/<orgKey>-translations.yaml} files off the classpath and
 * hands each bundle to the caller's {@link BundleSink}.
 *
 * <p>Shared by every feature library's own translation loader, because the scan, the {@code <orgKey>-}
 * filename convention and the per-file tally are identical wherever they appear — the seven existing
 * {@code Default*Loader}s each carry their own copy of exactly this code. What is <em>not</em> shared is
 * where a bundle is stored: the sink is the library's, writing to the library's own table, so the
 * features stay separable into services with separate databases.
 *
 * <p><strong>The scan is per feature, and the feature's own directory is what makes it so.</strong> The
 * pattern is {@code classpath*:default-translations/<feature>/*-translations.yaml}: {@code classpath*:}
 * finds a matching file in every jar, which is wanted — a host application may add its own bundles
 * beside a library's — but the seven libraries all name their file after the same tenant, so a shared
 * directory would hand every feature all seven files and every table would end up holding all seven
 * features' bundles. The directory is the discriminator a file name cannot be.
 *
 * <p>Nothing here can fail startup: every problem is logged and the next file or bundle is attempted.
 */
@Component
public class TranslationBundleImporter {

    private static final Logger LOG = LoggerFactory.getLogger(TranslationBundleImporter.class);
    private static final String FILE_SUFFIX = "-translations.yaml";
    private static final String LOCATION_TEMPLATE = "classpath*:default-translations/%s/*" + FILE_SUFFIX;

    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public TranslationBundleImporter(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    /**
     * What the caller does with one bundle, and what became of it. Returning an {@link Outcome} rather
     * than writing to a shared repository is what keeps storage the library's decision.
     */
    @FunctionalInterface
    public interface BundleSink {
        Outcome accept(String orgKey, TranslationBundleDocument.Entry entry);
    }

    /** What became of one bundle, for the per-file summary. */
    public enum Outcome {
        CREATED, MERGED, REJECTED
    }

    /**
     * Scans {@code default-translations/<featureName>/}, parses what it finds and feeds every bundle to
     * {@code sink}.
     *
     * @param featureName the calling library — {@code base-app}, {@code base-entity}, … It names both the
     *                    directory scanned and the log prefix, so the seven loaders running one after
     *                    another on the same startup read only their own files and say which they were.
     */
    public void importAll(String featureName, BundleSink sink) {
        String location = LOCATION_TEMPLATE.formatted(featureName);

        Resource[] resources;
        try {
            resources = resourceResolver.getResources(location);
        } catch (IOException e) {
            LOG.warn("[{}] Unable to scan for default translation files at {}", featureName, location, e);
            return;
        }

        if (resources.length == 0) {
            LOG.info("[{}] No default translation files found at {}", featureName, location);
            return;
        }

        for (Resource resource : resources) {
            load(featureName, resource, sink);
        }
    }

    private void load(String featureName, Resource resource, BundleSink sink) {
        String fileName = resource.getFilename();
        String orgKey = orgKeyOf(fileName);
        if (orgKey == null) {
            LOG.warn("[{}] Skipping default translation file '{}': the name does not follow the '<orgKey>{}' convention.",
                    featureName, fileName, FILE_SUFFIX);
            return;
        }

        TranslationBundleDocument document;
        try (InputStream input = resource.getInputStream()) {
            document = yamlMapper.readValue(input, TranslationBundleDocument.class);
        } catch (IOException e) {
            LOG.warn("[{}] Failed to read default translation file {}", featureName, fileName, e);
            return;
        }

        Tally tally = new Tally();
        for (TranslationBundleDocument.Entry entry : document.translations()) {
            tally.add(accept(featureName, orgKey, entry, fileName, sink));
        }
        LOG.info("[{}] Loaded default translations from {} into organization '{}': created={}, merged={}, rejected={}",
                featureName, fileName, orgKey, tally.created, tally.merged, tally.rejected);
    }

    private Outcome accept(String featureName, String orgKey, TranslationBundleDocument.Entry entry, String fileName, BundleSink sink) {
        if (entry == null || entry.locale() == null || entry.locale().isBlank()) {
            LOG.warn("[{}] Skipping a translation bundle in {}: the entry is null or names no locale.", featureName, fileName);
            return Outcome.REJECTED;
        }

        try {
            return sink.accept(orgKey, entry);
        } catch (RuntimeException e) {
            LOG.warn("[{}] Failed to store the '{}' translations of scope '{}' from {} in organization '{}'.",
                    featureName, entry.locale(), entry.scope(), fileName, orgKey, e);
            return Outcome.REJECTED;
        }
    }

    /**
     * Merges {@code overlay} into {@code base}, recursing into nested maps so two contributions to the
     * same bundle combine key by key rather than one replacing the other wholesale. A scalar in
     * {@code overlay} wins over the same key in {@code base}.
     *
     * <p>Deep merge rather than the create-or-skip the other default loaders use, because a bundle is not
     * one authored artifact: several jars may legitimately add keys to the same
     * {@code (orgKey, scope, locale)}, and classpath enumeration order must not decide whose survive.
     * Note the consequence — unlike a seeded app or entity definition, a seeded translation <em>does</em>
     * overwrite an edited value on restart. Bundles are shipped defaults, not tenant content.
     */
    public static Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> overlay) {
        Map<String, Object> merged = base == null ? new LinkedHashMap<>() : new LinkedHashMap<>(base);
        if (overlay == null) {
            return merged;
        }

        overlay.forEach((key, incoming) -> merged.merge(key, incoming, (existing, replacement) -> {
            if (existing instanceof Map<?, ?> existingMap && replacement instanceof Map<?, ?> replacementMap) {
                return deepMerge(asMessageMap(existingMap), asMessageMap(replacementMap));
            }
            return replacement;
        }));
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMessageMap(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }

    /** The part of {@code <orgKey>-translations.yaml} before the suffix, or {@code null} if there is none. */
    private static String orgKeyOf(String fileName) {
        if (fileName == null || !fileName.endsWith(FILE_SUFFIX)) {
            return null;
        }
        String orgKey = fileName.substring(0, fileName.length() - FILE_SUFFIX.length());
        return orgKey.isBlank() ? null : orgKey;
    }

    /** The per-file summary. */
    private static final class Tally {
        private int created;
        private int merged;
        private int rejected;

        private void add(Outcome outcome) {
            switch (outcome) {
                case CREATED -> created++;
                case MERGED -> merged++;
                case REJECTED -> rejected++;
            }
        }
    }
}
