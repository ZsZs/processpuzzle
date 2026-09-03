package com.processpuzzle.platformadmin.adapter.outbound;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Where Keycloak is and how to authenticate to its Admin REST API, bound from
 * {@code keycloak.admin.*}. Modelled on {@code processpuzzle-store}'s {@code MinioProperties}.
 *
 * <p>The credentials are a <b>service account</b> on a confidential client, not a human
 * administrator's password: the client-credentials grant needs no interactive login, so it survives
 * MFA being switched on for real administrators, and the account can be scoped to exactly
 * {@code create-realm} plus {@code realm-management} rather than to everything.
 *
 * <p>{@link #isConfigured()} is what decides whether the real adapter or
 * {@code NoOpIdentityRealmPort} is used. It checks the secret rather than the URL, because the URL
 * has a working default and the secret cannot: a deployment that forgot to set the secret would
 * otherwise fail on its first provisioning rather than at startup, and would fail with an opaque 401.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "keycloak.admin")
public class KeycloakAdminProperties {

    /** Base URL of the Keycloak server, without a trailing slash. */
    private String url = "http://localhost:7070";

    /**
     * Realm the service account itself lives in. {@code master} is where a client holding
     * {@code create-realm} has to live — realm creation is not a realm-scoped permission.
     */
    private String realm = "master";

    /** Confidential client whose service account performs the admin calls. */
    private String clientId = "platform-admin-service";

    /** Service-account secret. Empty disables the adapter entirely; see {@link #isConfigured()}. */
    private String clientSecret = "";

    /**
     * Public client created in every tenant realm, which the Angular applications authenticate
     * against. One name for all tenants on purpose: the frontend resolves the realm from the URL and
     * would otherwise have to know a per-tenant client id too.
     */
    private String tenantClientId = "processpuzzle-ui";

    /**
     * Redirect URI pattern registered on each tenant's client. {@code {orgKey}} is substituted with
     * the realm name, so a tenant's client cannot be used to redirect into another tenant's space.
     */
    private String tenantRedirectUri = "http://localhost:4200/{orgKey}/*";

    /** Web origin registered on each tenant's client, for the CORS preflight the SPA triggers. */
    private String tenantWebOrigin = "http://localhost:4200";

    /** Whether a usable admin client is configured at all. */
    public boolean isConfigured() {
        return clientSecret != null && !clientSecret.isBlank();
    }
}
