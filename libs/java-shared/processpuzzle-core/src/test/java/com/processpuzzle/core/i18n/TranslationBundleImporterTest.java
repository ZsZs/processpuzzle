package com.processpuzzle.core.i18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranslationBundleImporterTest {

    private ResourcePatternResolver resourceResolver;
    private TranslationBundleImporter importer;
    private List<String> accepted;

    @BeforeEach
    void setUp() {
        resourceResolver = mock(ResourcePatternResolver.class);
        importer = new TranslationBundleImporter(resourceResolver);
        accepted = new ArrayList<>();
    }

    /** A sink that records what it was handed, so a test can assert what reached the library. */
    private TranslationBundleImporter.BundleSink recordingSink() {
        return (orgKey, entry) -> {
            accepted.add("%s/%s/%s=%s".formatted(orgKey, entry.scope(), entry.locale(), entry.messages()));
            return TranslationBundleImporter.Outcome.CREATED;
        };
    }

    private static Resource file(String name, String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    private void given(Resource... resources) throws IOException {
        when(resourceResolver.getResources(anyString())).thenReturn(resources);
    }

    /**
     * The regression this guards is silent and total: with one shared directory, {@code classpath*:} hands
     * every feature all seven libraries' files — they all name theirs after the same tenant — so every
     * feature's table ends up holding every feature's bundles. The directory is the only discriminator,
     * because the file name cannot be one.
     */
    @Test
    void scansOnlyTheCallingFeaturesOwnDirectory() throws IOException {
        given();

        importer.importAll("base-widget", recordingSink());

        ArgumentCaptor<String> location = ArgumentCaptor.forClass(String.class);
        verify(resourceResolver).getResources(location.capture());
        assertThat(location.getValue()).isEqualTo("classpath*:default-translations/base-widget/*-translations.yaml");
    }

    @Test
    void readsEveryBundleOfAFileAndNamesTheOrganizationAfterTheFile() throws IOException {
        given(file("acme-translations.yaml", """
                translations:
                  - scope: order_admin
                    locale: en
                    messages:
                      module:
                        name: Order administration
                  - scope: order_admin
                    locale: de
                    messages:
                      module:
                        name: Auftragsverwaltung
                """));

        importer.importAll("base-app", recordingSink());

        assertThat(accepted).containsExactly(
                "acme/order_admin/en={module={name=Order administration}}",
                "acme/order_admin/de={module={name=Auftragsverwaltung}}");
    }

    /** `classpath*:` finds one file per contributing jar; all of them have to be read, not just the first. */
    @Test
    void readsEveryContributingFile() throws IOException {
        given(
                file("acme-translations.yaml", "translations:\n  - scope: a\n    locale: en\n    messages: {}\n"),
                file("acme-translations.yaml", "translations:\n  - scope: b\n    locale: en\n    messages: {}\n"));

        importer.importAll("base-app", recordingSink());

        assertThat(accepted).hasSize(2);
    }

    /** A bundle with no scope is the application's own root bundle — the one served with no scope segment. */
    @Test
    void treatsAnAbsentScopeAsTheRootBundle() throws IOException {
        given(file("acme-translations.yaml", "translations:\n  - locale: en\n    messages:\n      home: Home\n"));

        importer.importAll("base-app", recordingSink());

        assertThat(accepted).containsExactly("acme/_root/en={home=Home}");
    }

    @Test
    void skipsAFileWhoseNameDoesNotNameAnOrganization() throws IOException {
        given(file("translations.yaml", "translations:\n  - scope: a\n    locale: en\n    messages: {}\n"));

        importer.importAll("base-app", recordingSink());

        assertThat(accepted).isEmpty();
    }

    @Test
    void skipsABundleThatNamesNoLocale() throws IOException {
        given(file("acme-translations.yaml", "translations:\n  - scope: a\n    messages: {}\n"));

        importer.importAll("base-app", recordingSink());

        assertThat(accepted).isEmpty();
    }

    // Nothing the importer meets may fail startup: a malformed file, an unreadable classpath and a sink
    // that throws are all logged and stepped over.
    @Test
    void survivesAMalformedFile() throws IOException {
        given(file("acme-translations.yaml", "translations: [ this is not a bundle"));

        assertThatCode(() -> importer.importAll("base-app", recordingSink())).doesNotThrowAnyException();
        assertThat(accepted).isEmpty();
    }

    @Test
    void survivesAnUnscannableClasspath() throws IOException {
        when(resourceResolver.getResources(anyString())).thenThrow(new IOException("no classpath"));

        assertThatCode(() -> importer.importAll("base-app", recordingSink())).doesNotThrowAnyException();
    }

    @Test
    void survivesASinkThatThrows() throws IOException {
        given(file("acme-translations.yaml", """
                translations:
                  - scope: a
                    locale: en
                    messages: {}
                  - scope: b
                    locale: en
                    messages: {}
                """));

        TranslationBundleImporter.BundleSink failing = (orgKey, entry) -> {
            if ("a".equals(entry.scope())) {
                throw new IllegalStateException("boom");
            }
            return recordingSink().accept(orgKey, entry);
        };

        assertThatCode(() -> importer.importAll("base-app", failing)).doesNotThrowAnyException();
        assertThat(accepted).hasSize(1);
    }

    @Test
    void reportsNothingToDoWhenNoFileIsBundled() throws IOException {
        given();

        assertThatCode(() -> importer.importAll("base-app", recordingSink())).doesNotThrowAnyException();
        assertThat(accepted).isEmpty();
    }

    @Test
    void deepMergeCombinesNestedMapsRatherThanReplacingThem() {
        Map<String, Object> base = Map.of("module", Map.of("name", "Orders", "hint", "kept"), "top", "base");
        Map<String, Object> overlay = Map.of("module", Map.of("name", "Order administration"), "extra", "added");

        Map<String, Object> merged = TranslationBundleImporter.deepMerge(base, overlay);

        assertThat(merged).containsEntry("top", "base").containsEntry("extra", "added");
        assertThat(merged.get("module")).isEqualTo(Map.of("name", "Order administration", "hint", "kept"));
    }

    @Test
    void deepMergeLetsAScalarOverwriteAndToleratesNulls() {
        assertThat(TranslationBundleImporter.deepMerge(Map.of("k", "old"), Map.of("k", "new"))).containsEntry("k", "new");
        assertThat(TranslationBundleImporter.deepMerge(null, Map.of("k", "v"))).containsEntry("k", "v");
        assertThat(TranslationBundleImporter.deepMerge(Map.of("k", "v"), null)).containsEntry("k", "v");
    }

    /** A map replacing a scalar, and vice versa: the overlay wins outright rather than trying to merge. */
    @Test
    void deepMergeReplacesWhenTheShapesDisagree() {
        assertThat(TranslationBundleImporter.deepMerge(Map.of("k", "scalar"), Map.of("k", Map.of("nested", "v"))))
                .containsEntry("k", Map.of("nested", "v"));
        assertThat(TranslationBundleImporter.deepMerge(Map.of("k", Map.of("nested", "v")), Map.of("k", "scalar")))
                .containsEntry("k", "scalar");
    }
}
