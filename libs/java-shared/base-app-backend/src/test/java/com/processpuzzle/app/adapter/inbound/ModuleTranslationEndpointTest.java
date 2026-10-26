package com.processpuzzle.app.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.app.usecase.DownloadModuleTranslations;
import com.processpuzzle.app.usecase.GetModuleTranslationBundle;
import com.processpuzzle.app.usecase.GetModuleTranslationCoverage;
import com.processpuzzle.app.usecase.ModuleTranslationCoverageResult;
import com.processpuzzle.app.usecase.ModuleTranslationUploadResult;
import com.processpuzzle.app.usecase.UploadModuleTranslations;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

/** The adapter moves bytes and maps results; what it must get right is the channel, the headers and the mapping. */
class ModuleTranslationEndpointTest {

    private final DownloadModuleTranslations download = mock(DownloadModuleTranslations.class);
    private final UploadModuleTranslations upload = mock(UploadModuleTranslations.class);
    private final GetModuleTranslationCoverage coverage = mock(GetModuleTranslationCoverage.class);
    private final GetModuleTranslationBundle bundle = mock(GetModuleTranslationBundle.class);
    private final ModuleTranslationEndpoint endpoint = new ModuleTranslationEndpoint(download, upload, coverage, bundle);

    @Test
    void theDownloadIsAZipSavedAsAFileNamedAfterTheModule() throws Exception {
        when(download.execute("org", "claims")).thenReturn(new byte[] {'P', 'K', 3, 4});

        var response = endpoint.downloadModuleTranslations("org", "claims");

        assertThat(response.getHeaders().getContentType()).hasToString("application/zip");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"claims-translations.zip\"");
        assertThat(response.getBody().contentLength()).isEqualTo(4);
    }

    @Test
    void theUploadHandsTheFileNameAndContentToTheUseCaseAndMapsItsReport() {
        when(upload.execute(eq("org"), eq("claims"), eq("de.json"), any())).thenReturn(new ModuleTranslationUploadResult(
                List.of(new ModuleTranslationUploadResult.Language("de", 2, List.of("x"), List.of("y"), List.of("z")))));

        var report = endpoint.uploadModuleTranslations("org", "claims",
                new MockMultipartFile("file", "de.json", "application/json", "{}".getBytes())).getBody();

        assertThat(report.getLanguages()).singleElement().satisfies(de -> {
            assertThat(de.getLocale()).isEqualTo("de");
            assertThat(de.getStoredKeys()).isEqualTo(2);
            assertThat(de.getUnknownKeys()).containsExactly("x");
            assertThat(de.getMissingKeys()).containsExactly("y");
            assertThat(de.getUntranslatedKeys()).containsExactly("z");
        });
    }

    @Test
    void coverageIsMapped() {
        when(coverage.execute("org", "claims")).thenReturn(
                List.of(new ModuleTranslationCoverageResult("de", false, 3, 1, List.of("a"))));

        assertThat(endpoint.getModuleTranslationCoverage("org", "claims").getBody()).singleElement().satisfies(de -> {
            assertThat(de.getLocale()).isEqualTo("de");
            assertThat(de.getDefaultLocale()).isFalse();
            assertThat(de.getKeyCount()).isEqualTo(3);
            assertThat(de.getTranslatedCount()).isEqualTo(1);
            assertThat(de.getMissingKeys()).containsExactly("a");
        });
    }

    @Test
    void theBundleIsReadFromTheChannelTheRevisionNames() {
        when(bundle.execute("org", "claims", "de", Channel.RELEASED)).thenReturn(Map.of("module.name", "Schaeden"));

        assertThat(endpoint.getModuleTranslationBundle("org", "claims", "de", "released").getBody())
                .containsEntry("module.name", "Schaeden");
        endpoint.getModuleTranslationBundle("org", "claims", "de", null);
        verify(bundle).execute("org", "claims", "de", Channel.DRAFT);
    }
}
