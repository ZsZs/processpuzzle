package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.OrganizationSuspendedException;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.usecase.FindOrganization;
import com.processpuzzle.platformadmin.usecase.OrganizationGuard;
import org.springframework.stereotype.Component;

/**
 * The one thing every operation in this module does first: decide that {@code orgKey} names a real,
 * administerable tenant, and that the caller may administer it.
 *
 * <p>Three refusals, in this order, and the order is the point:
 *
 * <ol>
 *   <li><b>Membership.</b> {@link OrganizationGuard#requireDesign} — the {@code orgKey} path segment
 *       is not an authorization decision on its own, and without this check editing the URL is enough
 *       to administer another tenant's users. Design rights rather than mere access, because managing
 *       who may sign in is an administrative act, not a read.
 *   <li><b>Existence.</b> A 404 for an unknown key. Checked <em>after</em> membership so a
 *       non-member cannot enumerate which organizations exist by which keys answer 404 and which 403.
 *   <li><b>Status.</b> A suspended tenant's realm is disabled, so administering it would fail deep
 *       inside Keycloak with a message about a realm. {@code PROVISIONING} is refused for the
 *       stronger reason that its realm does not exist at all yet.
 * </ol>
 *
 * <p>Returns the realm name rather than {@code void}, so a caller cannot accidentally use the raw
 * path parameter after validating it — the value that comes back out is the one to use.
 */
@Component
public class TenantRealmResolver {

    private final FindOrganization findOrganization;
    private final OrganizationGuard guard;

    public TenantRealmResolver(FindOrganization findOrganization, OrganizationGuard guard) {
        this.findOrganization = findOrganization;
        this.guard = guard;
    }

    /**
     * @return the realm to administer; always equal to the tenant's own key
     * @throws com.processpuzzle.platformadmin.usecase.exception.OrganizationAccessDeniedException
     *         when the caller is not an administrator of this tenant
     * @throws com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException
     *         when no such organization exists
     * @throws OrganizationSuspendedException when the tenant's realm is disabled or not yet created
     */
    public String resolve(String orgKey) {
        guard.requireDesign(orgKey);
        Organization organization = findOrganization.executeUnguarded(orgKey);
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new OrganizationSuspendedException(orgKey);
        }
        return organization.getKey();
    }
}
