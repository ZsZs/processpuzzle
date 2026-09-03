/**
 * The one port Platform Admin depends on but cannot implement itself: the identity provider a
 * tenant's realm is created in
 * ({@link com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort}, implemented in this
 * module's own outbound adapter but replaceable).
 *
 * <p>It ships with an inert fallback
 * ({@link com.processpuzzle.platformadmin.usecase.port.NoOpIdentityRealmPort}) so the library and
 * its tests run with no Keycloak present.
 *
 * <p>{@code OrganizationAccessPolicy} was the second port here, and is now
 * {@link com.processpuzzle.core.tenancy.OrganizationAccessPolicy}. It never belonged to this
 * feature: deciding who the caller is is infrastructure every feature needs, and leaving it here
 * meant base-app compiled against platform-admin for nothing but an authorization check. This
 * module is now one of its consumers rather than its owner.
 *
 * <p>Exposed as the {@code port} named interface, separately from {@code usecase}: this is the side
 * of the module other code plugs into, not the side it calls.
 */
@NamedInterface("port")
package com.processpuzzle.platformadmin.usecase.port;

import org.springframework.modulith.NamedInterface;
