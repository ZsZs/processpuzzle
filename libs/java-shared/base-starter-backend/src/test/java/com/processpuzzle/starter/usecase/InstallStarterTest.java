package com.processpuzzle.starter.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationAccessPolicy;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.registry.StarterCatalog;
import com.processpuzzle.starter.registry.StarterRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class InstallStarterTest {

    private static final byte[] BUNDLE = "zip bytes".getBytes(StandardCharsets.UTF_8);

    private OrganizationAccessPolicy policy;
    private StarterRegistry registry;
    private ImportBundle importBundle;
    private InstallStarter installStarter;

    @BeforeEach
    void setUp() {
        policy = mock(OrganizationAccessPolicy.class);
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("policy", policy);
        OrganizationGuard guard = new OrganizationGuard(factory.getBeanProvider(OrganizationAccessPolicy.class));

        registry = mock(StarterRegistry.class);
        when(registry.catalog()).thenReturn(catalog(BundleReader.sha256(BUNDLE)));
        when(registry.bundle("inventory/1.0.0/bundle.zip")).thenReturn(BUNDLE);
        importBundle = mock(ImportBundle.class);
        installStarter = new InstallStarter(guard, new BrowseCatalog(registry), registry, importBundle);
    }

    private static StarterCatalog catalog(String sha256) {
        return new StarterCatalog(1, List.of(new StarterCatalog.Starter("inventory", "Inventory", null, null, null,
                null, null, null, List.of(new StarterCatalog.Version("1.0.0", null, null, 1, sha256,
                "inventory/1.0.0/bundle.zip")))));
    }

    @Test
    void importsTheDownloadedBundleAsTheRequestedStarter() {
        ImportReport applied = new ImportReport(false, ImportReport.Status.APPLIED, "inventory", "1.0.0", List.of(), List.of());
        when(importBundle.execute(eq("acme"), any(InputStream.class), eq(false), eq("alice"),
                eq(new ImportBundle.Expected("inventory", "1.0.0")))).thenReturn(applied);

        assertThat(installStarter.execute("acme", "inventory", "1.0.0", false, "alice")).isSameAs(applied);
    }

    @Test
    void refusesADownloadThatDoesNotMatchTheCatalog() {
        when(registry.catalog()).thenReturn(catalog(BundleReader.sha256("other".getBytes(StandardCharsets.UTF_8))));

        assertThatThrownBy(() -> installStarter.execute("acme", "inventory", "1.0.0", true, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(ImportRejectedException.Reason.INVALID);
                    assertThat(e.getReport().dryRun()).isTrue();
                    assertThat(e.getMessage()).contains("does not match the catalog's sha256");
                });
        verifyNoInteractions(importBundle);
    }

    @Test
    void anUnknownVersionIsNotFound() {
        assertThatThrownBy(() -> installStarter.execute("acme", "inventory", "9.9.9", false, null))
                .isInstanceOf(StarterNotFoundException.class);
        verify(registry, org.mockito.Mockito.never()).bundle(any());
    }

    @Test
    void checksDesignRightsBeforeTouchingTheRegistry() {
        doThrow(new OrganizationAccessDeniedException("acme")).when(policy).requireDesign("acme");

        assertThatThrownBy(() -> installStarter.execute("acme", "inventory", "1.0.0", false, null))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        verifyNoInteractions(registry);
    }
}
