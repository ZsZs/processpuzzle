package com.processpuzzle.core.identity;

/**
 * How a tenant maps onto Keycloak, derived from {@code keycloak.admin.*} once so that no two
 * features can disagree about it.
 */
public enum KeycloakTenancy {

    /** No admin secret: there is no identity provider to project into; callers stand down. */
    UNCONFIGURED,

    /** Every tenant is a realm named after its {@code orgKey} — the testbed's model. */
    REALM_PER_TENANT,

    /**
     * Every tenant is an Organization, by alias, inside {@code keycloak.admin.organization-realm} —
     * the Custom stack's model.
     */
    ORGANIZATION
}
