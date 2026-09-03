package com.processpuzzle.orgadmin.usecases.outbound;

import java.util.List;
import java.util.Optional;

/**
 * The tenant's user directory. One realm per organization, named after the {@code orgKey}.
 *
 * <p><b>The directory is the system of record.</b> Nothing in this module keeps a copy, so every
 * method here is the authoritative read or write — and a failure means the operation did not happen,
 * with no local state left inconsistent. That is why the failure maps to a retryable 503 rather than
 * to a 500.
 *
 * <p>Two things this port deliberately cannot do. It cannot create or delete a <em>realm</em>: realm
 * lifecycle belongs to {@code platform-admin}, and a tenant administrator must not be able to remove
 * their own organization's identity provider. And it cannot create a role — roles beyond
 * {@code org-admin} / {@code org-member} are the tenant's own, but minting one from a typo in a role
 * assignment would produce a role nothing ever matches, so {@link #replaceRoles} refuses a name the
 * realm does not declare instead.
 *
 * @see NoOpUserDirectoryPort
 */
public interface UserDirectoryPort {

    /**
     * Pages through the realm's users, optionally filtered.
     *
     * <p>{@code search} is free text over username, first name, last name and email, passed to the
     * provider's own search — not RSQL, because there is no database here to filter.
     */
    DirectoryPage findUsers(String realm, String search, int page, int size);

    /** One user by the provider's own id, or empty when the realm has no such user. */
    Optional<DirectoryUser> findUser(String realm, String userId);

    /**
     * Creates a user, enabled, with no credentials and a required action forcing a password reset on
     * first login — so the administrator never knows the invitee's password.
     *
     * @param roles realm roles to grant at creation; an empty list means {@code org-member} only
     * @return the created user, read back so the caller need not guess what the provider stored
     */
    DirectoryUser inviteUser(String realm, NewUser user, List<String> roles);

    /**
     * Replaces a user's profile fields and enabled flag.
     *
     * <p>{@code username} is not among them: it is what audit records name, so it is treated as
     * immutable even though the provider would allow a rename. Roles are not among them either —
     * they have their own operation, because the two are separate authorization decisions.
     */
    DirectoryUser updateUser(String realm, String userId, UserProfile profile);

    /** Deletes the user. Irreversible; prefer {@code enabled: false}. */
    void deleteUser(String realm, String userId);

    /** Every realm role the tenant declares, read live rather than from a fixed enum. */
    List<DirectoryRole> findRoles(String realm);

    /** The realm roles currently granted to one user. */
    List<DirectoryRole> findUserRoles(String realm, String userId);

    /**
     * Makes the user hold exactly {@code roles}, computing the grants and revocations from the
     * difference.
     *
     * <p>A full replacement rather than add/remove verbs, because the role-assignment screen presents
     * the realm's roles as a checkbox set and saves the whole set — and two concurrent editors of
     * that screen must not silently merge into a union neither of them chose.
     *
     * @throws com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownRoleException when a name
     *         is not a role the realm declares
     */
    List<DirectoryRole> replaceRoles(String realm, String userId, List<String> roles);

    /**
     * A user to create. No password field, on purpose: see {@link #inviteUser}.
     */
    record NewUser(String username, String email, String firstName, String lastName) {
    }

    /**
     * The mutable half of a user.
     *
     * @param enabled {@code null} leaves the flag as it is, so a profile edit does not have to know
     *                the current value to avoid changing it
     */
    record UserProfile(String email, String firstName, String lastName, Boolean enabled) {
    }
}
