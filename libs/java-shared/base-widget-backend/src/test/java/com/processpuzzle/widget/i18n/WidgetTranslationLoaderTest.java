package com.processpuzzle.widget.i18n;

import com.processpuzzle.core.i18n.TranslationBundleDocument;
import com.processpuzzle.core.i18n.TranslationBundleImporter;
import com.processpuzzle.core.i18n.TranslationBundleKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WidgetTranslationLoaderTest {

    private static final String ORG = "processpuzzle-testbed";

    private WidgetTranslationRepository repository;
    private TranslationBundleImporter importer;
    private WidgetTranslationLoader loader;

    @BeforeEach
    void setUp() {
        repository = mock(WidgetTranslationRepository.class);
        importer = mock(TranslationBundleImporter.class);
        loader = new WidgetTranslationLoader(repository, importer);
        when(repository.findById(any(TranslationBundleKey.class))).thenReturn(Optional.empty());
    }

    /** Drives the loader's sink with the bundles a seed file would have contained. */
    private void seedFileContaining(TranslationBundleDocument.Entry... entries) {
        doAnswer(call -> {
            TranslationBundleImporter.BundleSink sink = call.getArgument(1);
            for (TranslationBundleDocument.Entry entry : List.of(entries)) {
                sink.accept(ORG, entry);
            }
            return null;
        }).when(importer).importAll(anyString(), any());
    }

    @Test
    void storesABundleThatIsNotThereYet() {
        seedFileContaining(new TranslationBundleDocument.Entry("a_scope", "en", Map.of("k", "v")));

        loader.loadDefaults();

        ArgumentCaptor<WidgetTranslationBundle> saved = ArgumentCaptor.forClass(WidgetTranslationBundle.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOrgKey()).isEqualTo(ORG);
        assertThat(saved.getValue().getScope()).isEqualTo("a_scope");
        assertThat(saved.getValue().getLocale()).isEqualTo("en");
        assertThat(saved.getValue().getMessages()).containsEntry("k", "v");
    }

    // Merged rather than skipped, unlike the other default loaders: several jars may contribute keys to
    // one (orgKey, scope, locale), and classpath enumeration order must not decide whose survive.
    @Test
    void mergesIntoABundleThatIsAlreadyThere() {
        WidgetTranslationBundle existing =
                new WidgetTranslationBundle(ORG, "a_scope", "en", Map.of("kept", "old", "shared", "old"));
        when(repository.findById(new TranslationBundleKey(ORG, "a_scope", "en"))).thenReturn(Optional.of(existing));
        seedFileContaining(new TranslationBundleDocument.Entry("a_scope", "en", Map.of("shared", "new", "added", "new")));

        loader.loadDefaults();

        verify(repository).save(existing);
        assertThat(existing.getMessages())
                .containsEntry("kept", "old")
                .containsEntry("shared", "new")
                .containsEntry("added", "new");
    }

    @Test
    void namesItselfToTheImporterSoTheStartupLogCanTellTheSevenApart() {
        seedFileContaining();

        loader.loadDefaults();

        verify(importer).importAll(org.mockito.ArgumentMatchers.eq("base-widget"), any());
    }
}
