package com.processpuzzle.app.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.app.usecase.AppTranslationUploadResult;
import com.processpuzzle.app.usecase.DeleteAppTranslation;
import com.processpuzzle.app.usecase.DownloadAppTranslations;
import com.processpuzzle.app.usecase.GetAppTranslationBundle;
import com.processpuzzle.app.usecase.UploadAppTranslations;
import com.processpuzzle.core.definition.Channel;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

/** The endpoint only moves bytes: the use cases decide, so these tests check the mapping and the headers. */
class AppDefinitionTranslationEndpointTest {

    private final DownloadAppTranslations download = mock(DownloadAppTranslations.class);
    private final UploadAppTranslations upload = mock(UploadAppTranslations.class);
    private final GetAppTranslationBundle bundle = mock(GetAppTranslationBundle.class);
    private final DeleteAppTranslation delete = mock(DeleteAppTranslation.class);
    private final AppDefinitionTranslationEndpoint endpoint = new AppDefinitionTranslationEndpoint(download, upload, bundle, delete);

    @Test
    void theDownloadIsAZipNamedAfterTheApp() throws Exception {
        when(download.execute("org", "app")).thenReturn(new byte[] {1, 2, 3});

        var response = endpoint.downloadAppTranslations("org", "app");

        assertThat(response.getHeaders().getContentType()).hasToString("application/zip");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("app-translations.zip");
        assertThat(response.getBody().contentLength()).isEqualTo(3);
    }

    @Test
    void theUploadReportsWhatWasStoredPerLanguage() {
        when(upload.execute(ArgumentMatchers.eq("org"), ArgumentMatchers.eq("app"), ArgumentMatchers.eq("de.json"),
                ArgumentMatchers.any(InputStream.class)))
                .thenReturn(new AppTranslationUploadResult(List.of(new AppTranslationUploadResult.Language("de", 4))));

        var response = endpoint.uploadAppTranslations("org", "app", new MockMultipartFile("file", "de.json", "application/json", "{}".getBytes()));

        assertThat(response.getBody().getLanguages()).singleElement().satisfies(language -> {
            assertThat(language.getLocale()).isEqualTo("de");
            assertThat(language.getStoredKeys()).isEqualTo(4);
        });
    }

    @Test
    void theBundleIsReadFromTheChannelTheRevisionNames() {
        when(bundle.execute("org", "app", "en", Channel.RELEASED)).thenReturn(Map.of("a", "1"));

        var response = endpoint.getAppDefinitionTranslationBundle("org", "app", "en", "released");

        assertThat(response.getBody()).containsEntry("a", "1");
    }

    @Test
    void deletingAnswersNoContent() {
        var response = endpoint.deleteAppTranslation("org", "app", "en");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(delete).execute("org", "app", "en");
    }
}
