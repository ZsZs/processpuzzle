package com.processpuzzle.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Chat needs to know who is calling — a message has an author — so its paths require a bearer token even
 * though the rest of the tenant API is open until {@code processpuzzle.security.require-authentication} is
 * set. Here it is not set, which is how a stack runs today.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatPathAuthenticationTest {

    @LocalServerPort
    private int port;

    @Test
    void everyChatPathRefusesACallWithoutAToken() throws Exception {
        assertThat(status("GET", "/organizations/my-org/chat/definitions")).isEqualTo(401);
        assertThat(status("GET", "/organizations/my-org/chat/conversations?subjectType=ENTITY&subjectId=x")).isEqualTo(401);
        assertThat(status("POST", "/organizations/my-org/chat/conversations")).isEqualTo(401);
        assertThat(status("POST", "/organizations/my-org/chat/conversations/9d1c0000-0000-4000-8000-000000000001/messages")).isEqualTo(401);
    }

    @Test
    void theApiPrefixedPathIsGuardedToo() throws Exception {
        assertThat(status("GET", "/api/organizations/my-org/chat/definitions")).isEqualTo(401);
    }

    @Test
    void theRestOfTheTenantApiStaysOpenAsConfigured() throws Exception {
        assertThat(status("GET", "/organizations/my-org/event-definitions")).isNotEqualTo(401);
    }

    private int status(String method, String path) throws Exception {
        HttpRequest.BodyPublisher body = "POST".equals(method)
                ? HttpRequest.BodyPublishers.ofString("{}") : HttpRequest.BodyPublishers.noBody();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body)
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        }
    }
}
