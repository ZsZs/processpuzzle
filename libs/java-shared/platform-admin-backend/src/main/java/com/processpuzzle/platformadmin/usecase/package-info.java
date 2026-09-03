/**
 * The use cases of the Platform Admin feature, and the module's outward-facing surface: the
 * organization lifecycle (provision, find, find-all, update, delete, suspend, activate,
 * check-key, assign-admin) and the read-only billing queries.
 *
 * <p>Exposed as the {@code usecase} named interface. Two consumers reach in here:
 *
 * <ul>
 *   <li>{@code base-app} — which used to own this aggregate. Its {@code ProvisionTenant} calls
 *       {@link com.processpuzzle.platformadmin.usecase.ProvisionOrganization}, and every one of its
 *       own use cases calls {@link com.processpuzzle.core.tenancy.OrganizationGuard}.
 *   <li>{@code org-admin} — which resolves a tenant's realm through
 *       {@link com.processpuzzle.platformadmin.usecase.FindOrganization} and refuses unknown or
 *       suspended tenants before touching the user directory.
 * </ul>
 *
 * <p>{@code service} is not propagated and stays internal. {@code port} and {@code exception}
 * declare their own named interfaces: implementing a port is a different act from calling a use
 * case, and catching an exception is a third — base-app needs the exception types to keep answering
 * its own {@code /organizations*} endpoints with the same {@code errorId}s, but has no business
 * with the rest.
 */
@NamedInterface("usecase")
package com.processpuzzle.platformadmin.usecase;

import org.springframework.modulith.NamedInterface;
