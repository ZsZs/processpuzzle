package com.processpuzzle.platformadmin.adapter.outbound;

import com.processpuzzle.core.tenancy.TenantRoles;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.KeycloakAdminProperties;
import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates and manages one Keycloak realm per tenant through the Admin REST API.
 *
 * <p>Realm name is the {@code orgKey} verbatim — already a validated, unique, URL-safe slug, so
 * inventing a separate naming scheme would only create a mapping to get wrong.
 *
 * <p>{@link #createRealm} is three calls, not one, and the order matters: the realm, then the public
 * client the Angular applications authenticate against, then the two roles ProcessPuzzle interprets.
 * Each tolerates a 409, so a retry after a partial failure completes the missing steps instead of
 * refusing outright — the reason {@code PROVISIONING} is a durable state and not a lost cause.
 *
 * <p>Deliberately not a {@code @Component}: {@link IdentityRealmConfiguration} registers either this
 * or {@code NoOpIdentityRealmPort} depending on whether an admin secret is configured. Component
 * scanning it would give an unconfigured deployment an adapter that answers every call with a 503,
 * where what it should get is one that quietly does nothing.
 */
public class KeycloakAdminAdapter implements IdentityRealmPort {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakAdminAdapter.class);

    /** Forces the invitee to set their own password on first login; no administrator ever knows it. */
    private static final String UPDATE_PASSWORD = "UPDATE_PASSWORD";

    private static final String ADMIN_REALMS_PATH = "/admin/realms";
    private static final String ENABLED = "enabled";
    private static final int CONFLICT = 409;
    private static final int NOT_FOUND = 404;

    private final KeycloakAdminClient client;
    private final KeycloakAdminProperties properties;

    public KeycloakAdminAdapter(KeycloakAdminClient client, KeycloakAdminProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void createRealm(String realm, String displayName, String defaultLocale) {
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("realm", realm);
        representation.put("displayName", displayName);
        representation.put(ENABLED, true);
        if (defaultLocale != null && !defaultLocale.isBlank()) {
            representation.put("internationalizationEnabled", true);
            representation.put("defaultLocale", defaultLocale.split("-")[0]);
        }
        client.exchangeTolerating(HttpMethod.POST, ADMIN_REALMS_PATH, representation, CONFLICT);

        createTenantClient(realm);
        createRealmRole(realm, TenantRoles.ORG_ADMIN, "May administer this organization's users and roles.");
        createRealmRole(realm, TenantRoles.ORG_MEMBER, "Member of this organization.");
        LOG.info("Keycloak realm '{}' is ready.", realm);
    }

    @Override
    public void enableRealm(String realm) {
        setEnabled(realm, true);
    }

    @Override
    public void disableRealm(String realm) {
        setEnabled(realm, false);
    }

    @Override
    public void deleteRealm(String realm) {
        client.exchangeTolerating(HttpMethod.DELETE, realmPath(realm), null, NOT_FOUND);
        LOG.info("Keycloak realm '{}' deleted.", realm);
    }

    @Override
    public String createAdminUser(String realm, NewUser user, List<String> roles) {
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("username", user.username());
        representation.put("email", user.email());
        representation.put("firstName", user.firstName());
        representation.put("lastName", user.lastName());
        representation.put(ENABLED, true);
        representation.put("emailVerified", false);
        representation.put("requiredActions", List.of(UPDATE_PASSWORD));

        String userId = client.createAndReturnId(realmPath(realm) + "/users", representation)
                .orElseThrow(() -> new IdentityProviderUnavailableException(
                        "Keycloak created a user in realm '" + realm + "' but returned no id."));

        grantRealmRoles(realm, userId, roles);
        return userId;
    }

    /**
     * Grants realm roles by name, resolving each to the {@code {id, name}} pair Keycloak's
     * role-mapping endpoint requires. A name it does not recognise is skipped rather than failing the
     * whole grant: the alternative is a user created with no roles at all because one name was stale.
     */
    private void grantRealmRoles(String realm, String userId, List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        List<Map<String, Object>> mappings = new ArrayList<>(roles.size());
        for (String role : roles) {
            client.exchange(HttpMethod.GET, realmPath(realm) + "/roles/" + role, null, Map.class)
                    .ifPresentOrElse(
                            found -> mappings.add(Map.of("id", found.get("id"), "name", found.get("name"))),
                            () -> LOG.warn("Realm '{}' has no role '{}'; not granted.", realm, role));
        }
        if (!mappings.isEmpty()) {
            client.exchange(HttpMethod.POST,
                    realmPath(realm) + "/users/" + userId + "/role-mappings/realm",
                    mappings, null);
        }
    }

    /**
     * The public client every tenant realm gets. Standard flow with PKCE and no client secret,
     * because the caller is a browser application that cannot keep one.
     *
     * <p>The redirect URI is templated per tenant, so a token obtained through one tenant's client
     * cannot be redirected into another tenant's URL space.
     */
    private void createTenantClient(String realm) {
        Map<String, Object> clientRepresentation = new LinkedHashMap<>();
        clientRepresentation.put("clientId", properties.getTenantClientId());
        clientRepresentation.put("publicClient", true);
        clientRepresentation.put("standardFlowEnabled", true);
        clientRepresentation.put("directAccessGrantsEnabled", false);
        clientRepresentation.put("redirectUris",
                List.of(properties.getTenantRedirectUri().replace("{orgKey}", realm)));
        clientRepresentation.put("webOrigins", List.of(properties.getTenantWebOrigin()));
        clientRepresentation.put("attributes", Map.of("pkce.code.challenge.method", "S256"));

        client.exchangeTolerating(HttpMethod.POST, realmPath(realm) + "/clients",
                clientRepresentation, CONFLICT);
    }

    private void createRealmRole(String realm, String name, String description) {
        client.exchangeTolerating(HttpMethod.POST, realmPath(realm) + "/roles",
                Map.of("name", name, "description", description), CONFLICT);
    }

    private void setEnabled(String realm, boolean enabled) {
        client.exchange(HttpMethod.PUT, realmPath(realm),
                Map.of("realm", realm, ENABLED, enabled), null);
        LOG.info("Keycloak realm '{}' {}.", realm, enabled ? "enabled" : "disabled");
    }

    private static String realmPath(String realm) {
        return ADMIN_REALMS_PATH + "/" + realm;
    }
}
