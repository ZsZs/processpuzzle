package com.processpuzzle.app.domain;

/** Lifecycle of a tenant. Persisted as a string column, mirroring {@code rule.domain.Severity}. */
public enum OrganizationStatus {

    /**
     * Provisioning is still in flight. Not reachable today: provisioning creates the organization
     * and its starter app definition in a single transaction, so this state is never observable.
     * Kept because the contract declares it and an out-of-band step (Keycloak realm creation) will
     * need it.
     */
    PROVISIONING,

    /** The tenant is usable. */
    ACTIVE,

    /** Access revoked, data retained. */
    SUSPENDED
}
