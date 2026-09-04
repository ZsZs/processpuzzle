/**
 * Outbound adapters: the {@code IdentityRealmPort} implementation
 * ({@link com.processpuzzle.platformadmin.adapter.outbound.KeycloakAdminAdapter}), built on core's
 * {@link com.processpuzzle.core.identity.KeycloakAdminClient}.
 *
 * <p>Internal, and no longer a named interface. It was one — {@code keycloak} — so that org-admin
 * could share the admin client rather than open a second token cache against the same server. That
 * was a deliberate exception to the rule that a module publishes only ports and use cases, and it is
 * no longer needed: the client, its {@code keycloak.admin.*} properties and the
 * {@code IdentityProviderUnavailableException} it raises are infrastructure, not this feature's
 * domain, and now live in {@code processpuzzle-core}. Both modules reach them through core, so
 * there is still one token cache and one set of properties, without an edge between two features.
 *
 * <p>What stays here is what actually belongs to this module: realm lifecycle. org-admin can create
 * and disable users; only platform-admin creates and deletes realms.
 */
package com.processpuzzle.platformadmin.adapter.outbound;
