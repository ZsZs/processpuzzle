package com.processpuzzle.core.identity;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one authenticated conversation with Keycloak's Admin REST API.
 *
 * <p>Built on {@code RestClient} rather than {@code org.keycloak:keycloak-admin-client}. The Admin
 * API surface this platform needs is a dozen calls; the official client brings Jakarta REST, Jackson
 * and Resteasy transitively, into libraries that already have a working HTTP client and are careful
 * about their dependency tree. {@code RestToolInvocationAdapter} in base-workflow is the existing
 * outbound-HTTP precedent and this follows it, including the defensive
 * {@code ObjectProvider<RestClient.Builder>} constructor so the class is usable outside a Spring
 * context.
 *
 * <h2>Why this is shared with org-admin</h2>
 *
 * <p>{@code org-admin-backend} manages users in the same realms and therefore needs the same
 * conversation. It reaches this class through the {@code platformadmin :: keycloak} named interface
 * rather than opening its own: a second client would mean a second token cache expiring on its own
 * schedule and a second copy of {@code keycloak.admin.*} free to drift from this one. Exposing an
 * adapter package is a deliberate exception to the usual "ports and use cases only" rule, made
 * visible in both modules' {@code @ApplicationModule} declarations rather than hidden.
 *
 * <h2>Token handling</h2>
 *
 * <p>One cached client-credentials token, refreshed {@link #EXPIRY_MARGIN} before it expires so a
 * call cannot be issued with a token that dies in flight. Two threads racing here fetch two tokens
 * and one wins, which costs an extra round trip and breaks nothing.
 */
@Component
public class KeycloakAdminClient {

    /** Refresh this far ahead of expiry, so no request goes out with a token about to die. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final KeycloakAdminProperties properties;
    private final RestClient restClient;

    private String cachedToken;
    private Instant cachedTokenExpiry = Instant.EPOCH;

    @Autowired
    public KeycloakAdminClient(KeycloakAdminProperties properties,
                               ObjectProvider<RestClient.Builder> restClientBuilderProvider) {
        this(properties, restClientBuilderProvider.getIfAvailable(RestClient::builder));
    }

    public KeycloakAdminClient(KeycloakAdminProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.restClient = builder.baseUrl(properties.getUrl()).build();
    }

    /** Whether a usable admin client is configured; {@code false} means callers should stand down. */
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * Sends a request to the Admin API and returns the deserialized body.
     *
     * @param path path below the server root, starting with {@code /admin}
     * @param body request body, or {@code null} for none
     * @param responseType body type to read, or {@code null} to discard the response
     * @return the body, or {@link Optional#empty()} when the response had none
     * @throws IdentityProviderUnavailableException on any transport or protocol failure
     */
    public <T> Optional<T> exchange(HttpMethod method, String path, Object body, Class<T> responseType) {
        try {
            RestClient.RequestBodySpec request = restClient.method(method)
                    .uri(path)
                    .headers(headers -> headers.setBearerAuth(accessToken()));
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            RestClient.ResponseSpec response = request.retrieve();
            if (responseType == null) {
                response.toBodilessEntity();
                return Optional.empty();
            }
            return Optional.ofNullable(response.body(responseType));
        } catch (RestClientException ex) {
            throw new IdentityProviderUnavailableException(
                    "Keycloak admin call failed: " + method + " " + path, ex);
        }
    }

    /**
     * As {@link #exchange}, but tolerating the given HTTP statuses as success.
     *
     * <p>The idempotency contract on {@code IdentityRealmPort} needs this: deleting an absent realm
     * and creating an existing one are both retries of work that already succeeded, and reporting
     * them as failures would leave a caller unable to converge after a partial outage.
     */
    public void exchangeTolerating(HttpMethod method, String path, Object body, int... tolerated) {
        try {
            exchange(method, path, body, null);
        } catch (IdentityProviderUnavailableException ex) {
            if (!mentionsStatus(ex, tolerated)) {
                throw ex;
            }
        }
    }

    /** Reads a list of JSON objects, e.g. a realm's roles or a user search result. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getList(String path) {
        return exchange(HttpMethod.GET, path, null, List.class)
                .map(list -> (List<Map<String, Object>>) list)
                .orElseGet(List::of);
    }

    /**
     * Creates a resource and returns the id Keycloak encodes in the {@code Location} header.
     *
     * <p>{@code POST .../users} answers 201 with an empty body and the new id only in
     * {@code Location}. Reading it back with a search instead would be a second round trip and a race:
     * two users created concurrently with similar names can both match one search.
     */
    public Optional<String> createAndReturnId(String path, Object body) {
        try {
            HttpHeaders headers = restClient.post()
                    .uri(path)
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders();
            return Optional.ofNullable(headers.getFirst(HttpHeaders.LOCATION))
                    .map(location -> location.substring(location.lastIndexOf('/') + 1));
        } catch (RestClientException ex) {
            throw new IdentityProviderUnavailableException("Keycloak admin create failed: " + path, ex);
        }
    }

    /**
     * Whether the wrapped failure names one of {@code tolerated}.
     *
     * <p>Matching on the message rather than on a status code, because {@link #exchange} has already
     * wrapped the {@code RestClientException}. Keeping the status accessible would mean either a
     * richer exception type or letting the raw Spring exception escape the adapter; the string check
     * is the smaller compromise, and it is only ever consulted for the two statuses that mean "this
     * has already been done".
     */
    private static boolean mentionsStatus(RuntimeException ex, int... tolerated) {
        Throwable cause = ex.getCause();
        String message = String.valueOf(cause == null ? ex.getMessage() : cause.getMessage());
        for (int status : tolerated) {
            if (message.contains(String.valueOf(status))) {
                return true;
            }
        }
        return false;
    }

    private synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> token = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.getRealm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (token == null || token.get("access_token") == null) {
                throw new IdentityProviderUnavailableException(
                        "Keycloak returned no access_token for client " + properties.getClientId());
            }
            cachedToken = String.valueOf(token.get("access_token"));
            long expiresIn = token.get("expires_in") instanceof Number number ? number.longValue() : 60L;
            cachedTokenExpiry = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
            return cachedToken;
        } catch (RestClientException ex) {
            throw new IdentityProviderUnavailableException(
                    "Could not obtain a Keycloak admin token from " + properties.getUrl(), ex);
        }
    }
}
