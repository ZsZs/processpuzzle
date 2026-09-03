/**
 * Who the caller is, and which tenant they may act on.
 *
 * <p>{@link com.processpuzzle.core.tenancy.OrganizationGuard} is the single place any feature
 * consults {@link com.processpuzzle.core.tenancy.OrganizationAccessPolicy}, which the deploying
 * application supplies because identity lives there and not in any library — see
 * {@code JwtOrganizationAccessPolicy} in {@code apps/processpuzzle-testbed-backend}. With no such
 * bean the guard falls back to
 * {@link com.processpuzzle.core.tenancy.PermitAllOrganizationAccessPolicy}, which enforces nothing;
 * read its Javadoc before deploying.
 *
 * <p><b>Why this is in core.</b> These four types began in base-app, moved to platform-admin with
 * the {@code Organization} aggregate, and belong to neither. Nothing here reads a tenant row — the
 * guard is a façade over a port, and the port is answered from a token claim. Parking it in a
 * feature module meant every other feature took a compile dependency on that module to perform an
 * authorization check: base-app alone imported platform-admin in fourteen files for no other
 * reason. The rule the platform holds itself to is that feature libraries may talk only through
 * events or their own outbound ports, so shared infrastructure has to sit under them, not beside
 * them.
 */
package com.processpuzzle.core.tenancy;
