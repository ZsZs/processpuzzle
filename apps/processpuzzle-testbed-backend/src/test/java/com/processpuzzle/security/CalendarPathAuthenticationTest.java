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
 * A calendar can be public, so an anonymous visitor must be able to read one; an entry has an organizer, so
 * nobody anonymous may write one. The filter chain only decides that much — which calendar a caller may actually see
 * is base-calendar's business and is answered 404, which is what "reached the application" looks like here, the
 * organization having no calendars. Here {@code require-authentication} is not set, which is how a stack runs today.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalendarPathAuthenticationTest {

    private static final String ENTRY = "00000000-0000-4000-8000-000000000001";

    @LocalServerPort
    private int port;

    @Test
    void anAnonymousVisitorReachesTheReadPaths() throws Exception {
        assertThat(status("GET", "/organizations/my-org/calendar/calendars/team")).isEqualTo(404);
        assertThat(status("GET", "/organizations/my-org/calendar/calendars/team/entries/" + ENTRY)).isEqualTo(404);
        assertThat(status("GET", "/organizations/my-org/calendar/occurrences?calendars=team&from=2026-12-01T00:00:00Z&to=2026-12-08T00:00:00Z"))
                .isEqualTo(200);
    }

    @Test
    void everyWritePathRefusesACallWithoutAToken() throws Exception {
        assertThat(status("POST", "/organizations/my-org/calendar/calendars/team/entries")).isEqualTo(401);
        assertThat(status("PUT", "/organizations/my-org/calendar/calendars/team/entries/" + ENTRY)).isEqualTo(401);
        assertThat(status("DELETE", "/organizations/my-org/calendar/calendars/team/entries/" + ENTRY)).isEqualTo(401);
    }

    @Test
    void theApiPrefixedPathsFollowTheSameRules() throws Exception {
        // The application maps no /api prefix itself (the reverse proxy strips it), so a read gets past the filter
        // chain and ends in a plain 404; what matters here is that it is not 401, and that a write still is.
        assertThat(status("GET", "/api/organizations/my-org/calendar/calendars/team")).isNotEqualTo(401);
        assertThat(status("GET", "/api/organizations/my-org/calendar/occurrences?calendars=team&from=2026-12-01T00:00:00Z&to=2026-12-08T00:00:00Z"))
                .isNotEqualTo(401);
        assertThat(status("POST", "/api/organizations/my-org/calendar/calendars/team/entries")).isEqualTo(401);
        assertThat(status("DELETE", "/api/organizations/my-org/calendar/calendars/team/entries/" + ENTRY)).isEqualTo(401);
    }

    @Test
    void thePeopleAndTheirInvitationsNeedAToken() throws Exception {
        assertThat(status("GET", "/organizations/my-org/calendar/directory/users?q=bo")).isEqualTo(401);
        assertThat(status("GET", "/organizations/my-org/calendar/my-occurrences?from=2026-12-01T00:00:00Z&to=2026-12-08T00:00:00Z")).isEqualTo(401);
        assertThat(status("DELETE", "/organizations/my-org/calendar/users/sub-ann")).isEqualTo(401);
        assertThat(status("PUT", "/organizations/my-org/calendar/calendars/team/entries/" + ENTRY + "/attendance")).isEqualTo(401);
    }

    @Test
    void theDefinitionsStayOpenAsConfigured() throws Exception {
        assertThat(status("GET", "/organizations/my-org/calendar/definitions")).isNotEqualTo(401);
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
