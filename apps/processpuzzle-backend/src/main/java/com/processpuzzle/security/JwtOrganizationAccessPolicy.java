package com.processpuzzle.security;

import com.processpuzzle.platformadmin.usecase.exception.OrganizationAccessDeniedException;
import com.processpuzzle.platformadmin.usecase.port.OrganizationAccessPolicy;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * The real {@link OrganizationAccessPolicy}: the bean that finally replaces
 * {@code PermitAllOrganizationAccessPolicy} for this deployment.
 *
 * <p>The rule is the one the contracts have declared all along and nothing enforced: the
 * {@code orgKey} path segment is not an authorization decision, so it is compared against the realm
 * that issued the caller's token and answered 403 on mismatch. Editing the URL is no longer enough to
 * read another tenant's metadata.
 *
 * <h2>What an unauthenticated request gets, and why</h2>
 *
 * <p>Permitted — and this is the compromise that lets the resource server ship at all. The Angular
 * applications do not send a bearer token yet, so denying here would answer 403 to every existing
 * screen and every Playwright test. The protection for the surfaces that <em>are</em> new lives in
 * {@link SecurityConfig}'s filter chain instead, which requires authentication for
 * {@code /platform/**} and for the org-admin user-management paths regardless of this flag.
 *
 * <p>So the honest summary of this deployment's posture: <b>with</b> a token, tenant isolation is
 * enforced; <b>without</b> one, the legacy tenant API is as open as it was before. Setting
 * {@code processpuzzle.security.require-authentication: true} closes that, and any deployment
 * reachable from the internet must set it. The alternative — denying here by default — would have
 * meant shipping a resource server that nobody could turn on because it broke everything at once.
 *
 * <h2>Design rights are not yet distinguished from access</h2>
 *
 * <p>{@link #requireDesign} applies exactly the same check as {@link #requireAccess}. Requiring
 * {@code org-admin} for authoring would be the natural next step, and is deliberately not taken here:
 * nothing today grants a designer a distinct role, so demanding one would lock every tenant out of
 * its own designer. The two methods are kept separate at the port so that tightening one later is a
 * change in this class alone.
 */
@Component
public class JwtOrganizationAccessPolicy implements OrganizationAccessPolicy {

    private final CurrentPrincipal principal;

    public JwtOrganizationAccessPolicy(CurrentPrincipal principal) {
        this.principal = principal;
    }

    @Override
    public void requireAccess(String orgKey) {
        requireTenant(orgKey);
    }

    @Override
    public void requireDesign(String orgKey) {
        requireTenant(orgKey);
    }

    @Override
    public void requirePlatformAdmin() {
        if (!principal.isPlatformAdmin()) {
            throw new OrganizationAccessDeniedException("platform");
        }
    }

    /**
     * An empty or {@code null} collection means "any authenticated member" and must return true —
     * the port's Javadoc explains why the default runs in that direction. An unauthenticated caller
     * is treated as holding every role, consistently with {@link #requireAccess}: filtering nav items
     * away for a caller this policy has just permitted would hide entries in exactly the apps someone
     * had bothered to configure roles for.
     */
    @Override
    public boolean hasAnyRole(Collection<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return true;
        }
        if (!principal.isAuthenticated()) {
            return true;
        }
        return principal.isPlatformAdmin()
                || requiredRoles.stream().anyMatch(principal.authorities()::contains);
    }

    private void requireTenant(String orgKey) {
        if (!principal.isAuthenticated()) {
            return;
        }
        if (principal.isPlatformAdmin() || principal.isMemberOf(orgKey)) {
            return;
        }
        throw new OrganizationAccessDeniedException(orgKey);
    }
}
