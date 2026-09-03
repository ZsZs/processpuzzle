package com.processpuzzle.orgadmin;

import com.processpuzzle.orgadmin.usecases.inbound.TenantRealmResolver;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.usecase.FindOrganization;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationAccessPolicy;
import com.processpuzzle.core.tenancy.PermitAllOrganizationAccessPolicy;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared wiring for the org-admin unit tests.
 *
 * <p>{@link #resolverFor} builds a real {@link TenantRealmResolver} over a stubbed
 * {@code FindOrganization}, rather than mocking the resolver itself. That is the point: nearly every
 * refusal in this module — non-member, unknown tenant, suspended tenant — comes from the resolver, so
 * a test that mocked it away would prove only that a use case calls something.
 */
public final class OrgAdminTestFixtures {

    public static final String ORG_KEY = "my-org";
    public static final String USER_ID = "kc-user-1";

    private OrgAdminTestFixtures() {
    }

    /** A resolver that lets an ACTIVE tenant through. */
    public static TenantRealmResolver resolver() {
        return resolverFor(OrganizationStatus.ACTIVE, permissiveGuard());
    }

    /** A resolver over a tenant in the given lifecycle state. */
    public static TenantRealmResolver resolverFor(OrganizationStatus status) {
        return resolverFor(status, permissiveGuard());
    }

    public static TenantRealmResolver resolverFor(OrganizationStatus status, OrganizationGuard guard) {
        FindOrganization findOrganization = mock(FindOrganization.class);
        when(findOrganization.executeUnguarded(anyString())).thenAnswer(call ->
                new Organization(call.getArgument(0), "My Organization Ltd.", null, null, "en-GB", status));
        return new TenantRealmResolver(findOrganization, guard);
    }

    /** A resolver whose tenant does not exist. */
    public static TenantRealmResolver resolverForUnknownTenant() {
        FindOrganization findOrganization = mock(FindOrganization.class);
        when(findOrganization.executeUnguarded(anyString())).thenThrow(
                new com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException(ORG_KEY));
        return new TenantRealmResolver(findOrganization, permissiveGuard());
    }

    public static OrganizationGuard permissiveGuard() {
        return guardWith(new PermitAllOrganizationAccessPolicy());
    }

    public static OrganizationGuard denyingGuard() {
        return guardWith(new OrganizationAccessPolicy() {
            @Override
            public void requireDesign(String orgKey) {
                throw new OrganizationAccessDeniedException(orgKey);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static OrganizationGuard guardWith(OrganizationAccessPolicy policy) {
        ObjectProvider<OrganizationAccessPolicy> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(policy);
        return new OrganizationGuard(provider);
    }

    public static DirectoryUser user(String... roles) {
        return new DirectoryUser(USER_ID, "ada", "ada@my-org.example", "Ada", "Lovelace",
                true, false, Instant.parse("2026-08-01T00:00:00Z"), List.of(roles));
    }

    public static DirectoryRole role(String name) {
        return new DirectoryRole(name, null, "org-admin".equals(name) || "org-member".equals(name));
    }
}
