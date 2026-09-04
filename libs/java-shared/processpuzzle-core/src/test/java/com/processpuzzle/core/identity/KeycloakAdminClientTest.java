package com.processpuzzle.core.identity;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAdminClientTest {

    @Test
    void exchangesRequestsWithOneCachedBearerToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient client = client(builder);

        expectToken(server);
        server.expect(once(), requestTo("https://keycloak.example/admin/realms/acme"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andRespond(withSuccess("{\"name\":\"Acme\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://keycloak.example/admin/realms/other"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andRespond(withSuccess("{\"name\":\"Other\"}", MediaType.APPLICATION_JSON));

        assertThat(client.exchange(HttpMethod.GET, "/admin/realms/acme", null, Map.class))
                .contains(Map.of("name", "Acme"));
        assertThat(client.exchange(HttpMethod.GET, "/admin/realms/other", null, Map.class))
                .contains(Map.of("name", "Other"));
        server.verify();
    }

    @Test
    void sendsJsonBodiesAndReadsListsAndCreatedResourceIds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient client = client(builder);

        expectToken(server);
        server.expect(requestTo("https://keycloak.example/admin/realms/acme"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess());
        server.expect(requestTo("https://keycloak.example/admin/realms/acme/roles"))
                .andRespond(withSuccess("[{\"id\":\"role-1\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://keycloak.example/admin/realms/acme/users"))
                .andRespond(withCreatedEntity(java.net.URI.create("https://keycloak.example/admin/realms/acme/users/user-1")));

        assertThat(client.exchange(HttpMethod.PUT, "/admin/realms/acme", Map.of("enabled", true), null)).isEmpty();
        assertThat(client.getList("/admin/realms/acme/roles")).containsExactly(Map.of("id", "role-1"));
        assertThat(client.createAndReturnId("/admin/realms/acme/users", Map.of("username", "ada")))
                .contains("user-1");
        server.verify();
    }

    @Test
    void turnsTransportFailuresIntoDomainExceptionsAndToleratesConfiguredStatuses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient client = client(builder);

        expectToken(server);
        server.expect(requestTo("https://keycloak.example/admin/realms/acme"))
                .andRespond(withForbiddenRequest());
        server.expect(requestTo("https://keycloak.example/admin/realms/acme"))
                .andRespond(withForbiddenRequest());

        assertThatThrownBy(() -> client.exchange(HttpMethod.GET, "/admin/realms/acme", null, Map.class))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageContaining("GET /admin/realms/acme");
        client.exchangeTolerating(HttpMethod.GET, "/admin/realms/acme", null, 403);
        server.verify();
    }

    @Test
    void requiresAnAccessTokenAndReturnsEmptyWhenAListHasNoBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient client = client(builder);

        server.expect(requestTo("https://keycloak.example/realms/master/protocol/openid-connect/token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getList("/admin/realms/acme/roles"))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageContaining("no access_token");
        server.verify();
    }

    private static KeycloakAdminClient client(RestClient.Builder builder) {
        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setUrl("https://keycloak.example");
        properties.setClientSecret("secret");
        return new KeycloakAdminClient(properties, builder);
    }

    private static void expectToken(MockRestServiceServer server) {
        server.expect(once(), requestTo("https://keycloak.example/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess("{\"access_token\":\"token-1\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
    }
}
