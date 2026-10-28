package com.processpuzzle.app.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.app.usecase.DeleteAppAsset;
import com.processpuzzle.app.usecase.GetAppAssetContent;
import com.processpuzzle.app.usecase.ListAppAssets;
import com.processpuzzle.app.usecase.ModuleAssetContent;
import com.processpuzzle.app.usecase.ModuleAssetSummary;
import com.processpuzzle.app.usecase.UploadAppAsset;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

/** The headers are the test, as for {@link ModuleAssetEndpointTest}: an app asset is served exactly like a module's. */
class AppAssetEndpointTest {

    private final UploadAppAsset upload = mock(UploadAppAsset.class);
    private final ListAppAssets list = mock(ListAppAssets.class);
    private final GetAppAssetContent content = mock(GetAppAssetContent.class);
    private final DeleteAppAsset delete = mock(DeleteAppAsset.class);
    private final AppAssetEndpoint endpoint = new AppAssetEndpoint(upload, list, content, delete);

    private void serving(String path, String mimeType, byte[] bytes) {
        when(content.summaryOf("org", "app", path, Channel.RELEASED)).thenReturn(new ModuleAssetSummary(path, mimeType, bytes.length, "abc"));
        when(content.execute("org", "app", path, Channel.RELEASED)).thenReturn(new ModuleAssetContent(path, mimeType, "abc", bytes));
    }

    @Test
    void servesTheFileWithItsTypeItsHashAndNoSniffing() throws Exception {
        serving("cards/a.png", "image/png", new byte[] {1, 2});

        var response = endpoint.getAppAssetContent("org", "app", "cards/a.png", "released", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).hasToString("image/png");
        assertThat(response.getHeaders().getETag()).isEqualTo("\"abc\"");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getCacheControl()).contains("no-cache");
        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).isNull();
        assertThat(response.getBody().contentLength()).isEqualTo(2);
    }

    @Test
    void sandboxesSvgSoAVisitedUrlCannotRunItsScript() {
        serving("plan-race.svg", "image/svg+xml", new byte[] {1});

        var response = endpoint.getAppAssetContent("org", "app", "plan-race.svg", "released", null);

        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).startsWith("sandbox");
    }

    @Test
    void makesHtmlADownloadInsteadOfAPage() {
        serving("page.html", "text/html", new byte[] {1});

        var response = endpoint.getAppAssetContent("org", "app", "page.html", "released", null);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment").contains("page.html");
    }

    @Test
    void answersARevalidationOfUnchangedContentWithNotModifiedAndNoBody() {
        serving("a.png", "image/png", new byte[] {1});

        for (String header : List.of("\"abc\"", "W/\"abc\"", "\"zzz\", \"abc\"", "*")) {
            var response = endpoint.getAppAssetContent("org", "app", "a.png", "released", header);

            assertThat(response.getStatusCode().value()).as(header).isEqualTo(304);
            assertThat(response.getBody()).isNull();
        }
        verify(content, never()).execute(any(), any(), any(), any());
    }

    @Test
    void sendsTheContentWhenTheCallersTagIsStale() {
        serving("a.png", "image/png", new byte[] {1});

        assertThat(endpoint.getAppAssetContent("org", "app", "a.png", "released", "\"old\"").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void theUploadPassesTheFileAndMapsTheResult() {
        when(upload.execute("org", "app", "cards/a.png", "a.png", "image/png", new byte[] {1}))
                .thenReturn(new ModuleAssetSummary("cards/a.png", "image/png", 1, "abc"));

        var info = endpoint.uploadAppAsset("org", "app", new MockMultipartFile("file", "a.png", "image/png", new byte[] {1}), "cards/a.png").getBody();

        assertThat(info.getPath()).isEqualTo("cards/a.png");
        assertThat(info.getMimeType()).isEqualTo("image/png");
        assertThat(info.getSize()).isEqualTo(1);
        assertThat(info.getSha256()).isEqualTo("abc");
    }

    @Test
    void listsFromTheChannelTheRevisionNamesAndDeletesWithNoContent() {
        when(list.execute("org", "app", Channel.RELEASED)).thenReturn(List.of(new ModuleAssetSummary("a.png", "image/png", 1, "abc")));

        assertThat(endpoint.listAppAssets("org", "app", "released").getBody()).extracting("path").containsExactly("a.png");
        assertThat(endpoint.deleteAppAsset("org", "app", "a.png").getStatusCode().value()).isEqualTo(204);
        verify(delete).execute("org", "app", "a.png");
    }
}
