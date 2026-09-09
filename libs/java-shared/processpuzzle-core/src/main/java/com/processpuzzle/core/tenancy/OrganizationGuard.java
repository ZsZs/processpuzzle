package com.processpuzzle.core.tenancy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * The single place any feature consults {@link OrganizationAccessPolicy}. Use cases depend on this
 * component, never on the port directly.
 *
 * <p>The policy is resolved with {@link ObjectProvider#getIfUnique}, not
 * {@code @ConditionalOnMissingBean}. That condition is only reliable inside auto-configuration,
 * which is processed after user beans are registered — and putting an {@code @AutoConfiguration}
 * under {@code com.processpuzzle} would not help either, because the application scans that
 * package with {@code scanBasePackages} and the component scan picks such a class up before the
 * auto-configuration import is applied (exactly what already happens to
 * {@code LoggingAspectAutoConfiguration}). {@code getIfUnique} is order-independent: it returns
 * the application's bean when there is exactly one, and falls back otherwise.
 *
 * <p><b>Why this class is here and not in base-app, where it started.</b> It moved with the
 * {@code Organization} aggregate, and it had to shed something to make the trip: its
 * {@code filterRegions} / {@code isRouteReachable} methods walked base-app's {@code Region} and
 * {@code NavNode}, and a guard in {@code platformadmin} that reached back into {@code app} would be
 * a dependency cycle. Those two methods stayed behind as {@code app.usecase.service.NavVisibilityFilter},
 * which delegates {@link #isVisible} here. The split is not merely mechanical: deciding whether a
 * principal belongs to a tenant is a platform question, while deciding which nav entries that
 * principal may see is a base-app question that happens to need the platform's answer.
 */
@Component
public class OrganizationGuard {

    private final OrganizationAccessPolicy policy;

    public OrganizationGuard(ObjectProvider<OrganizationAccessPolicy> policyProvider) {
        this.policy = policyProvider.getIfUnique(PermitAllOrganizationAccessPolicy::new);
    }

    /** Rejects the call with 403 when the principal is not a member of {@code orgKey}. */
    public void requireAccess(String orgKey) {
        policy.requireAccess(orgKey);
    }

    /** Rejects the call with 403 when the principal may not author metadata for {@code orgKey}. */
    public void requireDesign(String orgKey) {
        policy.requireDesign(orgKey);
    }

    /**
     * Rejects the call with 403 unless the principal is ProcessPuzzle staff. Gates the whole
     * {@code /platform/**} surface, which acts across tenants and therefore cannot be gated by
     * comparing an {@code orgKey} against the principal's own.
     */
    public void requirePlatformAdmin() {
        policy.requirePlatformAdmin();
    }

    /** Whether the principal holds at least one of {@code roles}; an empty list means "any member". */
    public boolean isVisible(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return true;
        }
        return policy.hasAnyRole(roles);
    }
}
