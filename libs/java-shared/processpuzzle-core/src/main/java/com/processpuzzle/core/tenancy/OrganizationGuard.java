package com.processpuzzle.core.tenancy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * The single place any feature consults {@link OrganizationAccessPolicy}. Use cases depend on this
 * component, never on the port directly.
 *
 * <p>The policy is resolved with {@link ObjectProvider#getObject}, not
 * {@code @ConditionalOnMissingBean}. That condition is only reliable inside auto-configuration,
 * which is processed after user beans are registered — and putting an {@code @AutoConfiguration}
 * under {@code com.processpuzzle} would not help either, because the application scans that
 * package with {@code scanBasePackages} and the component scan picks such a class up before the
 * auto-configuration import is applied (exactly what already happens to
 * {@code LoggingAspectAutoConfiguration}). Resolution requires a single policy candidate (or a
 * primary candidate) and fails startup if none or multiple ambiguous candidates exist. A host
 * without identity must explicitly register {@link PermitAllOrganizationAccessPolicy} as a bean.
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

    private final ApplicationEventPublisher events;

    @Autowired
    public OrganizationGuard(ObjectProvider<OrganizationAccessPolicy> policyProvider, ApplicationEventPublisher events) {
        this.policy = policyProvider.getObject();
        this.events = events;
    }

    /** For callers that have no one to tell about the roles they name. */
    public OrganizationGuard(ObjectProvider<OrganizationAccessPolicy> policyProvider) {
        this(policyProvider, event -> { });
    }

    /**
     * Tells the organization's role catalog that these roles are now referred to — a navigation item
     * visible only to them, say — so each exists in the identity provider. See {@link RolesReferenced}.
     * It sits on the guard because every use case that stores a role list already holds one.
     */
    public void declareRoles(String orgKey, String description, Collection<String> roles) {
        if (roles != null && !roles.isEmpty()) {
            events.publishEvent(new RolesReferenced(orgKey, description, List.copyOf(roles)));
        }
    }

    /** Rejects the call with 403 when the principal is not a member of {@code orgKey}. */
    public void requireAccess(String orgKey) {
        policy.requireAccess(orgKey);
    }

    /** Rejects the call with 403 when the principal may not author metadata for {@code orgKey}. */
    public void requireDesign(String orgKey) {
        policy.requireDesign(orgKey);
    }

    /** Rejects the call with 403 when the principal may not administer {@code orgKey}'s users and roles. */
    public void requireAdministration(String orgKey) {
        policy.requireAdministration(orgKey);
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
