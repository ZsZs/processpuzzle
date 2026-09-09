package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.orgadmin.usecases.inbound.exception.OrganizationSuspendedException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownOrganizationException;
import com.processpuzzle.orgadmin.usecases.outbound.TenantRealmDirectory;
import org.springframework.beans.factory.ObjectProvider;
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
 *   <li><b>Status.</b> A tenant that is not administerable is refused, because its realm is disabled
 *       or does not exist yet, and administering it would otherwise fail deep inside Keycloak with a
 *       message about a realm.
 * </ol>
 *
 * <p>Returns the realm name rather than {@code void}, so a caller cannot accidentally use the raw
 * path parameter after validating it — the value that comes back out is the one to use.
 *
 * <p><b>Where the answer comes from.</b> {@link TenantRealmDirectory}, an outbound port of this
 * module. It was {@code platform-admin}'s {@code FindOrganization}, injected directly; the last two
 * refusals are the same refusals, but the tenant registry is now something an application supplies
 * rather than something this library compiles against. With no adapter wired the port answers by the
 * platform's naming convention, which is why the public repository's deployments need no registry
 * and lose nothing by having none — see {@link TenantRealmDirectory#BY_CONVENTION} for what that
 * does and does not preserve.
 */
@Component
public class TenantRealmResolver {

    private final TenantRealmDirectory tenants;
    private final OrganizationGuard guard;

    /**
     * {@code getIfUnique} rather than {@code @ConditionalOnMissingBean}, for the reason
     * {@link OrganizationGuard} documents at length: that condition is only reliable inside
     * auto-configuration, and this package is component-scanned by the host application.
     */
    public TenantRealmResolver(ObjectProvider<TenantRealmDirectory> tenants, OrganizationGuard guard) {
        this.tenants = tenants.getIfUnique(() -> TenantRealmDirectory.BY_CONVENTION);
        this.guard = guard;
    }

    /**
     * @return the realm to administer, as the directory named it — normally the tenant's own key
     * @throws com.processpuzzle.core.tenancy.OrganizationAccessDeniedException
     *         when the caller is not an administrator of this tenant
     * @throws UnknownOrganizationException when no such organization exists
     * @throws OrganizationSuspendedException when the tenant's realm is disabled or not yet created
     */
    public String resolve(String orgKey) {
        guard.requireDesign(orgKey);
        TenantRealmDirectory.Tenant tenant = tenants.find(orgKey)
                .orElseThrow(() -> new UnknownOrganizationException(orgKey));
        if (!tenant.administerable()) {
            throw new OrganizationSuspendedException(orgKey);
        }
        return tenant.realm();
    }
}
