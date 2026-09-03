/**
 * Ports Platform Admin depends on but cannot implement itself: who the caller is
 * ({@link com.processpuzzle.platformadmin.usecase.port.OrganizationAccessPolicy}, supplied by the
 * deploying application, which is where identity actually lives) and the identity provider a tenant's
 * realm is created in ({@link com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort},
 * implemented in this module's own outbound adapter but replaceable).
 *
 * <p>Both ship with a permissive or inert fallback —
 * {@link com.processpuzzle.platformadmin.usecase.port.PermitAllOrganizationAccessPolicy} and
 * {@link com.processpuzzle.platformadmin.usecase.port.NoOpIdentityRealmPort} — so the library and its
 * tests run with neither Spring Security nor Keycloak present. Read each one's Javadoc before
 * deploying: the first enforces nothing at all.
 *
 * <p>Exposed as the {@code port} named interface, separately from {@code usecase}: this is the side
 * of the module other code plugs into, not the side it calls. base-app consumes it because the
 * access policy moved here with the aggregate.
 */
@NamedInterface("port")
package com.processpuzzle.platformadmin.usecase.port;

import org.springframework.modulith.NamedInterface;
