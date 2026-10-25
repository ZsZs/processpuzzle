package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.tenancy.RolesReferenced;
import com.processpuzzle.orgadmin.usecases.outbound.RoleProvisioningPort;
import com.processpuzzle.orgadmin.usecases.outbound.TenantRealmDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnsureReferencedRolesTest {

    private RoleProvisioningPort provisioning;
    private TenantRealmDirectory tenants;
    private EnsureReferencedRoles listener;

    @BeforeEach
    void setUp() {
        provisioning = mock(RoleProvisioningPort.class);
        tenants = TenantRealmDirectory.BY_CONVENTION;
        listener = new EnsureReferencedRoles(provisioning, provider(() -> tenants));
    }

    @Test
    void createsTheNamedRolesInTheTenantsRealm() {
        listener.onRolesReferenced(new RolesReferenced("acme", "Referenced by documents",
                List.of("documentation-editor", "documentation-reviewer")));

        verify(provisioning).ensureRoles("acme", List.of("documentation-editor", "documentation-reviewer"),
                "Referenced by documents");
    }

    @Test
    void dropsBlanksDuplicatesAndTrimsNames() {
        listener.onRolesReferenced(new RolesReferenced("acme", "d", Arrays.asList(" editor ", "editor", "", null)));

        verify(provisioning).ensureRoles("acme", List.of("editor"), "d");
    }

    @Test
    void neverMintsReservedRoles() {
        listener.onRolesReferenced(new RolesReferenced("acme", "d", List.of("org-admin", "org-member",
                "platform-admin", "offline_access", "uma_authorization", "default-roles-acme")));

        verify(provisioning, never()).ensureRoles(anyString(), anyCollection(), any());
    }

    @Test
    void usesTheRealmTheTenantDirectoryNames() {
        tenants = orgKey -> Optional.of(new TenantRealmDirectory.Tenant("realm-of-" + orgKey, true));

        listener.onRolesReferenced(new RolesReferenced("acme", "d", List.of("editor")));

        verify(provisioning).ensureRoles(eq("realm-of-acme"), anyCollection(), any());
    }

    @Test
    void skipsSuspendedAndUnknownTenants() {
        tenants = orgKey -> Optional.of(new TenantRealmDirectory.Tenant(orgKey, false));
        listener.onRolesReferenced(new RolesReferenced("acme", "d", List.of("editor")));
        tenants = orgKey -> Optional.empty();
        listener.onRolesReferenced(new RolesReferenced("acme", "d", List.of("editor")));

        verify(provisioning, never()).ensureRoles(anyString(), anyCollection(), any());
    }

    @Test
    void anUnreachableIdentityProviderIsLoggedNotPropagated() {
        doThrow(new IdentityProviderUnavailableException("down")).when(provisioning)
                .ensureRoles(anyString(), anyCollection(), any());

        assertThatCode(() -> listener.onRolesReferenced(new RolesReferenced("acme", "d", List.of("editor"))))
                .doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TenantRealmDirectory> provider(Supplier<TenantRealmDirectory> current) {
        ObjectProvider<TenantRealmDirectory> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any(Supplier.class))).thenAnswer(call -> (TenantRealmDirectory) orgKey -> current.get().find(orgKey));
        return provider;
    }
}
