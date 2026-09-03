package com.processpuzzle.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CORS headers must survive the responses this application writes <em>instead of</em> reaching a
 * controller — above all the 401 on {@code /platform/**}, which every browser call from the
 * platform-admin application hits until it sends a staff token.
 *
 * <p>A real servlet container and a plain HTTP client rather than a MockMvc slice, on purpose: the
 * defect this guards was filter <em>ordering</em>, not policy. A {@code CorsFilter} bean is
 * auto-registered <em>around</em> the security chain at {@code LOWEST_PRECEDENCE}, so security's 401
 * short-circuited before any header was written and the browser reported "No
 * 'Access-Control-Allow-Origin' header is present" for an origin that was in fact allow-listed. Only
 * the full filter registration order can tell that apart from a missing origin.
 *
 * <p>Preflight is asserted too, but note that it never failed: {@code OPTIONS} is {@code permitAll}
 * and so did reach the outer filter. That asymmetry is what made the original report confusing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsHeadersOnRejectedRequestTest {

    /**
     * One of the origins named in {@code app.cors.allowed-origins}: the platform-admin container.
     *
     * <p>The default allow-list in {@code application.yaml} is the union of all six origins, because
     * one backend started by hand has to serve whichever frontend a developer runs. Each backend
     * deployment in docker-compose-ci.yaml narrows it to its own application stack — and this origin
     * belongs to the admin stack, which is the deployment these boot tests model; see the
     * {@code stack-realm} in application-unit-test.yaml.
     */
    private static final String ALLOWED_ORIGIN = "http://localhost:9091";

    private static final String ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";

    @LocalServerPort
    private int port;

    @Test
    void unauthenticatedPlatformRequestIsRejectedWithCorsHeaders() throws Exception {
        HttpResponse<String> response = send(request("/platform/organizations")
                .header("Origin", ALLOWED_ORIGIN)
                .GET());

        assertThat(response.statusCode())
                .as("no bearer token, so /platform/** must still be refused")
                .isEqualTo(401);
        assertThat(allowedOrigin(response))
                .as("a refusal without this header reaches the browser as an opaque CORS failure, "
                        + "hiding the 401 the frontend needs to see")
                .hasValue(ALLOWED_ORIGIN);
        assertThat(response.body())
                .as("and a 401 the frontend can see but not read is an empty snackbar; see "
                        + "ApiSecurityErrorHandler")
                .contains("\"errorId\":\"security.authentication-required\"")
                .contains("Authentication is required to access this resource.");
    }

    @Test
    void preflightForPlatformRequestIsAllowed() throws Exception {
        HttpResponse<String> response = send(request("/platform/organizations")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(allowedOrigin(response)).hasValue(ALLOWED_ORIGIN);
    }

    @Test
    void unknownOriginGetsNoCorsHeaders() throws Exception {
        HttpResponse<String> response = send(request("/platform/organizations")
                .header("Origin", "http://evil.example.com")
                .GET());

        assertThat(allowedOrigin(response)).isEmpty();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private static HttpResponse<String> send(HttpRequest.Builder request)
            throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static Optional<String> allowedOrigin(HttpResponse<String> response) {
        return response.headers().firstValue(ALLOW_ORIGIN_HEADER);
    }
}
