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
 * The same paths on a stack that closes the tenant API with {@code processpuzzle.security.require-authentication}, as
 * every internet-facing deployment must. A public calendar has to stay readable there, which is the point of listing
 * the read paths in the filter chain rather than leaving them to the flag; everything else is closed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "processpuzzle.security.require-authentication=true")
class CalendarPathAuthenticationRequiredTest {

    @LocalServerPort
    private int port;

    @Test
    void aPublicCalendarStaysReadableWithoutAToken() throws Exception {
        assertThat(status("GET", "/organizations/my-org/calendar/calendars/team")).isEqualTo(404);
        assertThat(status("GET", "/organizations/my-org/calendar/occurrences?calendars=team&from=2026-12-01T00:00:00Z&to=2026-12-08T00:00:00Z"))
                .isEqualTo(200);
    }

    @Test
    void writesAndDefinitionsAreClosed() throws Exception {
        assertThat(status("POST", "/organizations/my-org/calendar/calendars/team/entries")).isEqualTo(401);
        assertThat(status("GET", "/organizations/my-org/calendar/definitions")).isEqualTo(401);
        assertThat(status("PUT", "/organizations/my-org/calendar/definitions/team")).isEqualTo(401);
    }

    @Test
    void theRestOfTheTenantApiIsClosedToo() throws Exception {
        assertThat(status("GET", "/organizations/my-org/event-definitions")).isEqualTo(401);
    }

    private int status(String method, String path) throws Exception {
        HttpRequest.BodyPublisher body = "POST".equals(method) || "PUT".equals(method)
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
