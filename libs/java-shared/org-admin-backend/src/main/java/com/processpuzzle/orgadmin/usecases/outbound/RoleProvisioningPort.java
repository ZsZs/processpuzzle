package com.processpuzzle.orgadmin.usecases.outbound;

import java.util.Collection;

/**
 * Creates the realm roles other features refer to, so that My Profile and the role-assignment screen
 * can offer them.
 *
 * <p>Separate from {@link UserDirectoryPort}, which deliberately cannot create a role: minting one
 * from a typo in a role <em>assignment</em> would produce a role nothing ever matches. Roles named by
 * an <em>author</em> — in a document, a book, a navigation item — are a different intent, and are
 * created through here.
 *
 * <p>Create-only and idempotent: a role that exists is left exactly as it is, and nothing is ever
 * removed, because a realm's roles are one namespace shared with the platform's own and with whatever
 * an administrator made by hand.
 */
public interface RoleProvisioningPort {

    /**
     * Creates every role of {@code roleNames} that {@code realm} does not yet declare.
     *
     * @param description the description given to the roles created; existing roles keep theirs
     * @throws com.processpuzzle.core.identity.IdentityProviderUnavailableException when the provider
     *         could not be reached; the caller decides whether that matters
     */
    void ensureRoles(String realm, Collection<String> roleNames, String description);
}
