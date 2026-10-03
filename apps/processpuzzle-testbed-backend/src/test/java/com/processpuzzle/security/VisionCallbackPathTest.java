package com.processpuzzle.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The vision server's notifications must reach base-ai-backend's endpoint even when the tenant API
 * requires a bearer token, because they carry their own per-job {@code X-Callback-Token} instead. With
 * authentication required, an unknown job must therefore be answered by the endpoint (404), not by the
 * security chain (401) — while a neighbouring tenant path still is refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "processpuzzle.security.require-authentication=true")
class VisionCallbackPathTest {

    @LocalServerPort
    private int port;

    @Test
    void aNotificationReachesTheEndpointWithoutABearerToken() throws Exception {
        String body = "{\"jobId\":\"" + UUID.randomUUID() + "\",\"kind\":\"ENROLLMENT\",\"status\":\"DONE\"}";

        HttpResponse<String> response = post("/organizations/my-org/vision-notifications", body);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("ai.vision-job.not-found");
    }

    @Test
    void theRestOfTheTenantApiStillRequiresAToken() throws Exception {
        HttpResponse<String> response = post("/organizations/my-org/media-uploads",
                "{\"purpose\":\"ENROLLMENT_PHOTO\",\"fileName\":\"a.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1}");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Callback-Token", "anything")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
