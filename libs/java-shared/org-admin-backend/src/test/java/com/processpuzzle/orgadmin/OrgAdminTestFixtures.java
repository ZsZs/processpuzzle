package com.processpuzzle.orgadmin;

import com.processpuzzle.orgadmin.usecases.inbound.TenantRealmResolver;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.TenantRealmDirectory;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationAccessPolicy;
import com.processpuzzle.core.tenancy.PermitAllOrganizationAccessPolicy;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared wiring for the org-admin unit tests.
 *
 * <p>{@link #resolverFor} builds a real {@link TenantRealmResolver} over a stubbed
 * {@link TenantRealmDirectory}, rather than mocking the resolver itself. That is the point: nearly
 * every refusal in this module — non-member, unknown tenant, suspended tenant — comes from the
 * resolver, so a test that mocked it away would prove only that a use case calls something.
 *
 * <p>The stub is the port and no longer platform-admin's {@code FindOrganization}, so these fixtures
 * speak in {@code administerable} rather than in an {@code OrganizationStatus}. A tenant's lifecycle
 * state is the registry's vocabulary, and this module never sees it.
 */
public final class OrgAdminTestFixtures {

    public static final String ORG_KEY = "my-org";
    public static final String USER_ID = "kc-user-1";

    private OrgAdminTestFixtures() {
    }

    /** A resolver that lets an administerable tenant through. */
    public static TenantRealmResolver resolver() {
        return resolverFor(true, permissiveGuard());
    }

    /** A resolver over a tenant that is, or is not, administerable. */
    public static TenantRealmResolver resolverFor(boolean administerable) {
        return resolverFor(administerable, permissiveGuard());
    }

    public static TenantRealmResolver resolverFor(boolean administerable, OrganizationGuard guard) {
        return resolverOver(
                orgKey -> Optional.of(new TenantRealmDirectory.Tenant(orgKey, administerable)), guard);
    }

    /** A resolver whose tenant does not exist. */
    public static TenantRealmResolver resolverForUnknownTenant() {
        return resolverOver(orgKey -> Optional.empty(), permissiveGuard());
    }

    /** The seam the fixtures above go through: a real resolver over a stub directory. */
    public static TenantRealmResolver resolverOver(TenantRealmDirectory directory, OrganizationGuard guard) {
        return new TenantRealmResolver(providerOf(directory), guard);
    }

    /**
     * An {@code ObjectProvider} answering with exactly {@code instance}. The resolver and the guard
     * both resolve their collaborator through {@code getIfUnique}, so a test that wants a stub in
     * place has to hand them a provider rather than the stub itself.
     */
    @SuppressWarnings("unchecked")
    public static <T> ObjectProvider<T> providerOf(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(instance);
        return provider;
    }

    /**
     * A provider with no bean behind it, which invokes the caller's fallback supplier — what the real
     * {@code ObjectProvider} does, and the only way to exercise a port's default through the
     * constructor that resolves it. Stubbing {@code getIfUnique} to return {@code null} would not do:
     * that is a contract the real implementation never breaks, so a collaborator is entitled to
     * assume the value is non-null.
     */
    @SuppressWarnings("unchecked")
    public static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any()))
                .thenAnswer(call -> ((Supplier<T>) call.getArgument(0)).get());
        return provider;
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
