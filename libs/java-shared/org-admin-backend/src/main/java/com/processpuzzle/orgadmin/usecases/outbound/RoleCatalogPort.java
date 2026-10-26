package com.processpuzzle.orgadmin.usecases.outbound;

/**
 * Writes to an organization's role catalog on behalf of its administrator, in addition to the
 * create-only {@link RoleProvisioningPort} an author's reference goes through.
 *
 * <p>Every method answers the question the use case already settled — the role exists, or does not,
 * and the caller may change it — so an adapter does not repeat those checks. {@code directory} is what
 * {@link UserDirectoryPort} calls it: the realm, or the organization's alias when tenants are
 * Keycloak Organizations.
 *
 * <p>Not atomic with anything: a delete that follows {@link #hasHolders} can still remove a role
 * somebody was just given. The window is an administrator's two clicks, and the identity provider is
 * the system of record either way.
 */
public interface RoleCatalogPort extends RoleProvisioningPort {

    /** Declares the role. Its name has been validated and is known to be free. */
    void createRole(String directory, String name, String description);

    /** Replaces the role's description; a {@code null} one clears it. */
    void updateDescription(String directory, String name, String description);

    /** Removes the role, and with it whatever grants it. An absent role is success. */
    void deleteRole(String directory, String name);

    /** Whether at least one user holds the role. Existence, not a count: that is all a guard needs. */
    boolean hasHolders(String directory, String name);
}
