package com.processpuzzle.platformadmin.usecase.port;

import java.util.List;

/**
 * The identity provider a tenant's realm lives in. One realm per organization, named after the
 * {@code orgKey} — which is already a validated, unique, URL-safe slug, so it needs no separate
 * naming scheme.
 *
 * <p><b>Nothing here can participate in a database transaction.</b> Every method is a network call
 * to another system with its own durability, and there is no distributed transaction between the two.
 * That constraint is why the organization lifecycle is arranged the way it is: the row is written and
 * committed first, and the realm call happens afterwards from
 * {@code adapter.inbound.OrganizationRealmProvisioner}. A tenant can therefore be observed as
 * {@code PROVISIONING} — a durable, retryable state — but never as {@code ACTIVE} with no realm
 * behind it.
 *
 * <p>Implementations should be idempotent where the operation permits it: {@link #createRealm} on an
 * existing realm and {@link #deleteRealm} on an absent one are both retries of work that already
 * succeeded, and must not fail the caller.
 *
 * @see NoOpIdentityRealmPort
 */
public interface IdentityRealmPort {

    /**
     * Creates the tenant's realm, its public {@code processpuzzle-biz} client and the two realm roles
     * ProcessPuzzle itself interprets ({@link com.processpuzzle.core.tenancy.TenantRoles#ORG_ADMIN},
     * {@link com.processpuzzle.core.tenancy.TenantRoles#ORG_MEMBER}).
     *
     * <p>Idempotent: an already-existing realm is left alone rather than reported as an error, so a
     * retry after a partial failure converges instead of getting stuck.
     *
     * @param realm the realm name; always the tenant's {@code orgKey}
     * @param displayName shown on the realm's own login page
     * @param defaultLocale BCP-47 tag, or {@code null} to leave the provider's default
     */
    void createRealm(String realm, String displayName, String defaultLocale);

    /** Re-enables a disabled realm, so its members can obtain tokens again. */
    void enableRealm(String realm);

    /**
     * Disables the realm. Members cannot obtain a token; nothing is deleted, and
     * {@link #enableRealm} reverses it. This is what suspension means at the identity layer — the
     * alternative, deleting and recreating, would lose every user in the tenant.
     */
    void disableRealm(String realm);

    /** Deletes the realm and every user in it. Idempotent: an absent realm is not an error. */
    void deleteRealm(String realm);

    /**
     * Creates a user in {@code realm} and grants it {@code roles}.
     *
     * <p>Created without credentials and with a required action forcing a password reset on first
     * login, so no administrator ever knows the invitee's password. The identity provider's own id is
     * returned, which is what every ProcessPuzzle reference to a user records.
     *
     * @return the provider's opaque user id
     */
    String createAdminUser(String realm, NewUser user, List<String> roles);

    /**
     * A user to create. Deliberately has no password field: see {@link #createAdminUser}.
     *
     * @param username login name; immutable in practice, because audit records name it
     * @param email delivery address for the password-reset mail
     * @param firstName may be {@code null}
     * @param lastName may be {@code null}
     */
    record NewUser(String username, String email, String firstName, String lastName) {
    }
}
