package com.processpuzzle.document.usecase.port;

/**
 * The fallback {@link DocumentAccessPolicy}: permits everything, by inheriting every default.
 *
 * <p>This enforces no tenant isolation and no document roles. It exists so the feature is usable —
 * and testable — before an identity provider is wired into the backend, exactly as
 * {@code PermitAllOrganizationAccessPolicy} does for base-app. Deliberately <em>not</em> a
 * {@code @Component}: {@code DocumentGuard} instantiates it as a fallback, so a real policy bean in
 * the deploying application wins without any ordering or bean-name coordination.
 */
public class PermitAllDocumentAccessPolicy implements DocumentAccessPolicy {
    // every method inherits its permitting default
}
