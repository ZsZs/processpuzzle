package com.processpuzzle.platformadmin.domain;

/** Lifecycle of a tenant. Persisted as a string column, mirroring {@code rule.domain.Severity}. */
public enum OrganizationStatus {

    /**
     * The row is committed but the tenant's identity realm does not exist yet.
     *
     * <p>Genuinely observable, unlike while base-app owned this enum. Creating a Keycloak realm is a
     * network call, so it cannot join the transaction that writes the row: {@code
     * ProvisionOrganization} commits {@code PROVISIONING}, and an after-commit handler creates the
     * realm and flips the status to {@link #ACTIVE}. A row still in this state means realm creation
     * failed — durable and retryable, rather than a tenant rolled back out from under a realm that
     * already exists.
     */
    PROVISIONING,

    /** The tenant is usable. */
    ACTIVE,

    /** Access revoked, data retained. */
    SUSPENDED
}
