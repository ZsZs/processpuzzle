package com.processpuzzle.app.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.app.usecase.DeleteModuleAsset;
import com.processpuzzle.app.usecase.GetModuleAssetContent;
import com.processpuzzle.app.usecase.ListModuleAssets;
import com.processpuzzle.app.usecase.ModuleAssetContent;
import com.processpuzzle.app.usecase.ModuleAssetSummary;
import com.processpuzzle.app.usecase.UploadModuleAsset;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

/** What the browser is allowed to do with an uploaded file is decided here, so the headers are the test. */
class ModuleAssetEndpointTest {

    private final UploadModuleAsset upload = mock(UploadModuleAsset.class);
    private final ListModuleAssets list = mock(ListModuleAssets.class);
    private final GetModuleAssetContent content = mock(GetModuleAssetContent.class);
    private final DeleteModuleAsset delete = mock(DeleteModuleAsset.class);
    private final ModuleAssetEndpoint endpoint = new ModuleAssetEndpoint(upload, list, content, delete);

    private void serving(String path, String mimeType, byte[] bytes) {
        when(content.find("org", "claims", path, Channel.RELEASED)).thenReturn(new ModuleAssetSummary(path, mimeType, bytes.length, "abc"));
        when(content.execute("org", "claims", path, Channel.RELEASED)).thenReturn(new ModuleAssetContent(path, mimeType, "abc", bytes));
    }

    @Test
    void servesTheFileWithItsTypeItsHashAndNoSniffing() throws Exception {
        serving("docs/a.md", "text/markdown", new byte[] {1, 2});

        var response = endpoint.getModuleAssetContent("org", "claims", "docs/a.md", "released", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/markdown");
        assertThat(response.getHeaders().getETag()).isEqualTo("\"abc\"");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getCacheControl()).contains("no-cache");
        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).isNull();
        assertThat(response.getBody().contentLength()).isEqualTo(2);
    }

    @Test
    void sandboxesSvgSoAVisitedUrlCannotRunItsScript() {
        serving("logo.svg", "image/svg+xml", new byte[] {1});

        var response = endpoint.getModuleAssetContent("org", "claims", "logo.svg", "released", null);

        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).startsWith("sandbox");
    }

    @Test
    void makesHtmlADownloadInsteadOfAPage() {
        serving("page.html", "text/html", new byte[] {1});

        var response = endpoint.getModuleAssetContent("org", "claims", "page.html", "released", null);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment").contains("page.html");
    }

    @Test
    void answersARevalidationOfUnchangedContentWithNotModifiedAndNoBody() {
        serving("a.md", "text/markdown", new byte[] {1});

        for (String header : List.of("\"abc\"", "W/\"abc\"", "\"zzz\", \"abc\"", "*")) {
            var response = endpoint.getModuleAssetContent("org", "claims", "a.md", "released", header);

            assertThat(response.getStatusCode().value()).as(header).isEqualTo(304);
            assertThat(response.getBody()).isNull();
        }
        verify(content, never()).execute(any(), any(), any(), any());
    }

    @Test
    void sendsTheContentWhenTheCallersTagIsStale() {
        serving("a.md", "text/markdown", new byte[] {1});

        assertThat(endpoint.getModuleAssetContent("org", "claims", "a.md", "released", "\"old\"").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void theUploadPassesTheFileAndMapsTheResult() {
        when(upload.execute("org", "claims", "docs/a.md", "a.md", "text/markdown", new byte[] {1}))
                .thenReturn(new ModuleAssetSummary("docs/a.md", "text/markdown", 1, "abc"));

        var info = endpoint.uploadModuleAsset("org", "claims", new MockMultipartFile("file", "a.md", "text/markdown", new byte[] {1}), "docs/a.md").getBody();

        assertThat(info.getPath()).isEqualTo("docs/a.md");
        assertThat(info.getMimeType()).isEqualTo("text/markdown");
        assertThat(info.getSize()).isEqualTo(1);
        assertThat(info.getSha256()).isEqualTo("abc");
    }

    @Test
    void listsFromTheChannelTheRevisionNamesAndDeletesWithNoContent() {
        when(list.execute("org", "claims", Channel.RELEASED)).thenReturn(List.of(new ModuleAssetSummary("a.md", "text/markdown", 1, "abc")));

        assertThat(endpoint.listModuleAssets("org", "claims", "released").getBody()).extracting("path").containsExactly("a.md");
        assertThat(endpoint.deleteModuleAsset("org", "claims", "a.md").getStatusCode().value()).isEqualTo(204);
        verify(delete).execute("org", "claims", "a.md");
    }
}
