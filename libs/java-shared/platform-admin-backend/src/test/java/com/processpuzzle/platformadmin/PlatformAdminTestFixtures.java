package com.processpuzzle.platformadmin;

import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationAccessPolicy;
import com.processpuzzle.core.tenancy.PermitAllOrganizationAccessPolicy;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared wiring for the platform-admin unit tests. The guard is built for real rather than mocked,
 * so a use-case test proves that the guard actually consults its policy and not merely that a call
 * happens. Mirrors base-app's {@code AppTestFixtures}, which is where these three guard factories
 * were until the aggregate moved.
 */
public final class PlatformAdminTestFixtures {

    public static final String ORG_KEY = "my-org";

    private PlatformAdminTestFixtures() {
    }

    /** The guard a deployment without an access policy ends up with. */
    public static OrganizationGuard permissiveGuard() {
        return guardWith(new PermitAllOrganizationAccessPolicy());
    }

    /** A guard whose policy denies membership, design rights and staff authority alike. */
    public static OrganizationGuard denyingGuard() {
        return guardWith(new OrganizationAccessPolicy() {
            @Override
            public void requireAccess(String orgKey) {
                throw new OrganizationAccessDeniedException(orgKey);
            }

            @Override
            public void requireDesign(String orgKey) {
                throw new OrganizationAccessDeniedException(orgKey);
            }

            @Override
            public void requirePlatformAdmin() {
                throw new OrganizationAccessDeniedException("platform");
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static OrganizationGuard guardWith(OrganizationAccessPolicy policy) {
        ObjectProvider<OrganizationAccessPolicy> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(policy);
        return new OrganizationGuard(provider);
    }

    /** An ACTIVE tenant with every descriptive field populated. */
    public static Organization organization() {
        return organization(OrganizationStatus.ACTIVE);
    }

    public static Organization organization(OrganizationStatus status) {
        return new Organization(ORG_KEY, "My Organization Ltd.", "Insurance.", "ops@my-org.example",
                "en-GB", status);
    }
}
