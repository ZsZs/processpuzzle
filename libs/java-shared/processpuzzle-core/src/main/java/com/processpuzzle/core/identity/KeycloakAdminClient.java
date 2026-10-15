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
import java.util.OptionalInt;

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
 * <h2>Who shares it</h2>
 *
 * <p>Every feature that writes into Keycloak goes through this one client — base-workflow projecting
 * its role catalog, org-admin managing a tenant's users, invitations and self-registration — because
 * {@code core} is an open module both already depend on. A second client would mean a second token
 * cache expiring on its own schedule and a second copy of {@code keycloak.admin.*} free to drift
 * from this one.
 *
 * <p>This class only speaks the Admin API. How a tenant maps onto Keycloak — a realm of its own, or
 * an Organization in a shared realm — is {@link KeycloakTenancy} and {@link KeycloakTenantDirectory},
 * so that the features keep only their own semantics.
 *
 * <h2>Token handling</h2>
 *
 * <p>One cached client-credentials token, refreshed {@link #EXPIRY_MARGIN} before it expires so a
 * call cannot be issued with a token that dies in flight. Two threads racing here fetch two tokens
 * and one wins, which costs an extra round trip and breaks nothing.
 */
@Component
public class KeycloakAdminClient {

    /** Keycloak's answer when the resource is already there — on a create, usually a retry. */
    public static final int HTTP_CONFLICT = 409;

    /** Keycloak's answer when the resource is not there. */
    public static final int HTTP_NOT_FOUND = 404;

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
     * The realm tenants share as Organizations, or empty for realm-per-tenant. See
     * {@link KeycloakAdminProperties#getOrganizationRealm()}.
     */
    public Optional<String> organizationRealm() {
        return properties.isOrganizationMode() ? Optional.of(properties.getOrganizationRealm()) : Optional.empty();
    }

    /** How tenants map onto Keycloak in this deployment. */
    public KeycloakTenancy tenancy() {
        if (!properties.isConfigured()) {
            return KeycloakTenancy.UNCONFIGURED;
        }
        return properties.isOrganizationMode() ? KeycloakTenancy.ORGANIZATION : KeycloakTenancy.REALM_PER_TENANT;
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
        return exchangeAuthenticated(method, path, body, responseType, accessToken());
    }

    /**
     * Reads one resource, answering empty when Keycloak says 404 — the way to ask "is it there?"
     * without sending the absent case down the failure path. Every other failure is raised as by
     * {@link #exchange}.
     */
    public <T> Optional<T> find(String path, Class<T> responseType) {
        try {
            return exchange(HttpMethod.GET, path, null, responseType);
        } catch (IdentityProviderUnavailableException ex) {
            if (hasStatus(ex, HTTP_NOT_FOUND)) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private <T> Optional<T> exchangeAuthenticated(HttpMethod method, String path, Object body,
                                                 Class<T> responseType, String token) {
        try {
            RestClient.RequestBodySpec request = restClient.method(method)
                    .uri(path)
                    .headers(headers -> headers.setBearerAuth(token));
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
     * As {@link #exchange}, but tolerating the given Admin API response statuses as success.
     * Transport failures and token-acquisition failures are never tolerated.
     *
     * <p>The idempotency contract on {@code IdentityRealmPort} needs this: deleting an absent realm
     * and creating an existing one are both retries of work that already succeeded, and reporting
     * them as failures would leave a caller unable to converge after a partial outage.
     */
    public void exchangeTolerating(HttpMethod method, String path, Object body, int... tolerated) {
        String token = accessToken();
        try {
            exchangeAuthenticated(method, path, body, null, token);
        } catch (IdentityProviderUnavailableException ex) {
            if (!hasStatus(ex, tolerated)) {
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
     * Posts an {@code application/x-www-form-urlencoded} body and discards the response.
     *
     * <p>A few Admin API calls take a form rather than JSON, for instance
     * {@code .../organizations/{id}/members/invite-existing-user}, whose user id is a form field.
     *
     * @param path path below the server root, starting with {@code /admin}
     * @param form the form fields
     * @throws IdentityProviderUnavailableException on any transport or protocol failure
     */
    public void postForm(String path, MultiValueMap<String, String> form) {
        String token = accessToken();
        try {
            restClient.post()
                    .uri(path)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new IdentityProviderUnavailableException(
                    "Keycloak admin call failed: " + HttpMethod.POST + " " + path, ex);
        }
    }

    private static boolean hasStatus(IdentityProviderUnavailableException ex, int... tolerated) {
        OptionalInt actual = ex.getHttpStatus();
        if (actual.isEmpty()) {
            return false;
        }
        for (int status : tolerated) {
            if (actual.getAsInt() == status) {
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
