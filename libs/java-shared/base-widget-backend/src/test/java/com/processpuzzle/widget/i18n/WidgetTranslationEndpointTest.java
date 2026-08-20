package com.processpuzzle.widget.i18n;

import com.processpuzzle.core.i18n.AbstractTranslationBundle;
import com.processpuzzle.core.i18n.TranslationBundleKey;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WidgetTranslationEndpointTest {

    private static final String ORG = "processpuzzle-testbed";

    private WidgetTranslationRepository repository;
    private WidgetTranslationEndpoint endpoint;

    @BeforeEach
    void setUp() {
        repository = mock(WidgetTranslationRepository.class);
        endpoint = new WidgetTranslationEndpoint(repository);
        when(repository.findById(any(TranslationBundleKey.class))).thenReturn(Optional.empty());
    }

    private void seeded(String scope, String locale, Map<String, Object> messages) {
        when(repository.findById(new TranslationBundleKey(ORG, scope, locale)))
                .thenReturn(Optional.of(new WidgetTranslationBundle(ORG, scope, locale, messages)));
    }

    @Test
    void servesASeededScopedBundleAsItWasStored() {
        seeded("a_scope", "en", Map.of("module", Map.of("name", "Administration")));

        assertThat(endpoint.getWidgetScopedTranslations(ORG, "a_scope", "en").getBody())
                .isEqualTo(Map.of("module", Map.of("name", "Administration")));
    }

    /** The root bundle is the one with no scope segment; it is stored under the ROOT_SCOPE sentinel. */
    @Test
    void servesTheRootBundleFromTheRootScopeSentinel() {
        seeded(AbstractTranslationBundle.ROOT_SCOPE, "en", Map.of("home", "Home"));

        assertThat(endpoint.getWidgetTranslations(ORG, "en").getBody()).isEqualTo(Map.of("home", "Home"));
    }

    // 200-with-empty rather than 404: the loader falls back to an empty bundle either way, and a 404 per
    // unseeded scope would make a broken backend indistinguishable from an untranslated locale.
    @Test
    void answersAnUnseededScopeWithAnEmptyBundleRatherThanNotFound() {
        var response = endpoint.getWidgetScopedTranslations(ORG, "never_seeded", "en");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void keepsTheLocalesOfOneScopeApart() {
        seeded("a_scope", "en", Map.of("k", "English"));
        seeded("a_scope", "de", Map.of("k", "Deutsch"));

        assertThat(endpoint.getWidgetScopedTranslations(ORG, "a_scope", "en").getBody()).containsEntry("k", "English");
        assertThat(endpoint.getWidgetScopedTranslations(ORG, "a_scope", "de").getBody()).containsEntry("k", "Deutsch");
    }
}
