package com.processpuzzle.document.usecase.port;

import java.util.Collection;

/**
 * Outbound port deciding what the current principal may do with an organization's documents.
 * Supplied by the deploying application, which is where identity actually lives — there is no
 * Spring Security on the classpath of this library.
 *
 * <p>Deliberately this module's own port rather than base-app's {@code OrganizationAccessPolicy},
 * which is the same shape. base-document does not depend on base-app, and it must not: the app
 * shell is expected to host documents as its primary content type, so an edge in this direction
 * would invert that and eventually close the cycle. A deploying application implements both ports
 * with one adapter over its identity provider, which is a few lines there and keeps the two
 * features independently usable — the whole point of the Modulith boundaries.
 *
 * <p>Every method defaults to permitting, so an implementation only has to override what it
 * actually enforces. That direction matters for {@link #hasAnyRole}: a port shaped as
 * {@code Set<String> currentRoles()} would return an empty set without an identity provider, and
 * every document with roles configured would then be denied — refusing access precisely in the
 * organizations someone had bothered to configure.
 *
 * @see PermitAllDocumentAccessPolicy
 */
public interface DocumentAccessPolicy {

    /**
     * Rejects the call when the principal is not a member of {@code orgKey}. The contract requires
     * 403 here: the {@code orgKey} path segment is not an authorization decision on its own, and
     * without this check editing the URL is enough to read another tenant's documents.
     *
     * @throws com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException when denied
     */
    default void requireAccess(String orgKey) {
        // permitted by default
    }

    /**
     * Whether the principal holds at least one of {@code requiredRoles}. An empty or {@code null}
     * collection means "any authenticated member" and must return {@code true}.
     */
    default boolean hasAnyRole(Collection<String> requiredRoles) {
        return true;
    }

    /**
     * Whether there is an authenticated principal at all. Only consulted for documents that are
     * <em>not</em> public: anonymous access to published public content is a decision the document
     * itself carries, not one this port makes.
     */
    default boolean isAuthenticated() {
        return true;
    }

    /**
     * Who the principal is, for {@code createdBy} / {@code publishedBy} audit fields. {@code null}
     * when unknown — audit data is recorded when available and left empty otherwise, rather than
     * blocking a write that is authorized.
     */
    default String currentPrincipal() {
        return null;
    }
}
