/**
 * Outbound adapters: the authenticated conversation with Keycloak's Admin REST API
 * ({@link com.processpuzzle.platformadmin.adapter.outbound.KeycloakAdminClient}) and the
 * {@code IdentityRealmPort} implementation built on it
 * ({@link com.processpuzzle.platformadmin.adapter.outbound.KeycloakAdminAdapter}).
 *
 * <p>Exposed as the {@code keycloak} named interface, which is a deliberate exception to the rule
 * that a module publishes only ports and use cases. {@code org-admin-backend} manages users in the
 * same realms and needs the same conversation; giving it its own client would mean two token caches
 * expiring on independent schedules and two copies of {@code keycloak.admin.*} free to drift apart.
 * Sharing one is the lesser evil, and naming the interface makes the coupling visible in both
 * modules' {@code @ApplicationModule} declarations rather than leaving it to be discovered.
 *
 * <p>What is <em>not</em> shared: {@code KeycloakAdminAdapter} itself. Realm lifecycle belongs to
 * this module alone — org-admin can create and disable users, never realms.
 */
@NamedInterface("keycloak")
package com.processpuzzle.platformadmin.adapter.outbound;

import org.springframework.modulith.NamedInterface;
