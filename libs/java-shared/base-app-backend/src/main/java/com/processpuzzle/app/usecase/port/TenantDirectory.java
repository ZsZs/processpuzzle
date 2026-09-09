package com.processpuzzle.app.usecase.port;

import java.util.Optional;

/**
 * Outbound port answering the two questions this module has about a tenant it does not own: does
 * {@code orgKey} exist, and what is its default locale.
 *
 * <p>base-app used to inject {@code platform-admin}'s {@code OrganizationRepository} and ask it
 * directly — one feature library reading another's persistence. That is the shape that does not
 * survive these Modulith modules becoming services: every {@code existsById} would turn into a
 * synchronous cross-service call on a hot path, discovered at deployment rather than at design time.
 * Stating it as a port makes the question explicit and leaves the answering to whoever composes the
 * application.
 *
 * <p><b>The implementation is deliberately not in this library.</b> An adapter here would still
 * require base-app to compile against platform-admin, which is the whole of what this port exists to
 * avoid. It belongs in the composition root, alongside the {@code OrganizationAccessPolicy}
 * implementation, which is where the two modules are already known to each other. Swapping that
 * adapter for one that speaks HTTP is then a single class in one application and no change here.
 *
 * <p>Both methods default to permitting: with no adapter wired, base-app assumes every tenant exists
 * and has no preferred locale, rather than rejecting perfectly good requests. Same direction as
 * {@link EntityNameRegistry} — a library that cannot answer a question must not answer it with "no".
 *
 * @see Tenant
 */
public interface TenantDirectory {

    /** Whether {@code orgKey} names a tenant. */
    default boolean exists(String orgKey) {
        return true;
    }

    /** The tenant, when it is known and the directory can describe it. */
    default Optional<Tenant> find(String orgKey) {
        return Optional.empty();
    }

    /**
     * What base-app needs to know about a tenant, and nothing more — no status, no billing, no
     * contact details. Keeping the projection this narrow is what stops the port from drifting back
     * into a copy of the aggregate.
     *
     * @param orgKey the tenant's key
     * @param defaultLocale BCP-47 tag the shell activates for this tenant, or {@code null}
     */
    record Tenant(String orgKey, String defaultLocale) {
    }
}
