package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.usecase.OrganizationDetails;
import com.processpuzzle.platformadmin.usecase.ProvisionOrganization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.processpuzzle.platformadmin.PlatformAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The seeder for the tenants a fresh deployment starts with.
 *
 * <p>It exists because base-app used to do this: its {@code default-apps/<orgKey>-apps.yaml} carried
 * an {@code organization} block and its loader provisioned the tenant when the key was free. Three
 * properties matter more than the happy path, and each is pinned below — it must run before every
 * other seed loader, it must never touch an existing tenant, and nothing in it may fail startup.
 */
class DefaultOrganizationLoaderTest {

    private static final String FILE = ORG_KEY + "-organization.yaml";

    private ProvisionOrganization provisionOrganization;
    private OrganizationRepository repository;

    @BeforeEach
    void setUp() {
        provisionOrganization = mock(ProvisionOrganization.class);
        repository = mock(OrganizationRepository.class);
        when(repository.existsById(anyString())).thenReturn(false);
    }

    /** The bundled file: proves the shipped YAML actually parses, not merely that parsing works. */
    @Test
    void provisionsTheBundledTenant() {
        loaderOver(new PathMatchingResourcePatternResolver()).loadDefaults();

        ArgumentCaptor<OrganizationDetails> details = ArgumentCaptor.forClass(OrganizationDetails.class);
        verify(provisionOrganization).execute(eq("processpuzzle-testbed"), details.capture());
        assertThat(details.getValue().name()).isEqualTo("ProcessPuzzle Testbed");
        assertThat(details.getValue().defaultLocale()).isEqualTo("en");
    }

    /**
     * The file name decides the tenant, not the document. A file copied between deployments must not
     * silently seed the tenant it was copied from — the same rule every other seed file follows.
     */
    @Test
    void theFileNameDecidesTheTenant_notTheKeyInTheDocument() {
        loaderOver(resolverFor(yaml("other-org-organization.yaml",
                "key: processpuzzle-testbed\nname: Copied\n"))).loadDefaults();

        verify(provisionOrganization).execute(eq("other-org"), any());
    }

    /**
     * Create-only. A restart against a persistent database must not overwrite a name or locale an
     * operator changed by hand.
     */
    @Test
    void anExistingTenantIsLeftExactlyAsItIs() {
        when(repository.existsById(ORG_KEY)).thenReturn(true);

        loaderOver(resolverFor(yaml(FILE, "name: Renamed\n"))).loadDefaults();

        verify(provisionOrganization, never()).execute(anyString(), any());
    }

    @Test
    void aDocumentWithoutANameIsProvisionedUnderItsOwnKey() {
        loaderOver(resolverFor(yaml(FILE, "description: Only a description.\n"))).loadDefaults();

        ArgumentCaptor<OrganizationDetails> details = ArgumentCaptor.forClass(OrganizationDetails.class);
        verify(provisionOrganization).execute(eq(ORG_KEY), details.capture());
        assertThat(details.getValue().name()).isEqualTo(ORG_KEY);
        assertThat(details.getValue().description()).isEqualTo("Only a description.");
    }

    @Test
    void aResourceNotNamedAfterATenantIsSkipped() {
        loaderOver(resolverFor(yaml("notes.txt", "name: Nope\n"),
                new ByteArrayResource("name: Nameless".getBytes(StandardCharsets.UTF_8))))
                .loadDefaults();

        verify(provisionOrganization, never()).execute(anyString(), any());
    }

    @Test
    void aDeploymentBundlingNoFilesSeedsNothing() {
        loaderOver(resolverFor()).loadDefaults();

        verify(provisionOrganization, never()).execute(anyString(), any());
    }

    /**
     * A convenience that refuses to boot would be worse than one that seeds nothing, so each failure
     * below has to leave the application started.
     */
    @Test
    void anUnscannableClasspathIsLoggedRatherThanFailingStartup() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(anyString())).thenThrow(new IOException("no such classpath"));

        assertThatCode(() -> loaderOver(resolver).loadDefaults()).doesNotThrowAnyException();

        verify(provisionOrganization, never()).execute(anyString(), any());
    }

    @Test
    void anUnreadableFileIsSkippedWithoutFailingStartup() {
        assertThatCode(() -> loaderOver(resolverFor(yaml(FILE, "name: [\n"))).loadDefaults())
                .doesNotThrowAnyException();

        verify(provisionOrganization, never()).execute(anyString(), any());
    }

    @Test
    void anEmptyDocumentIsSkipped() {
        loaderOver(resolverFor(yaml(FILE, "\n"))).loadDefaults();

        verify(provisionOrganization, never()).execute(anyString(), any());
    }

    @Test
    void aFailingProvisioningIsSurvived() {
        doThrow(new IllegalStateException("no database"))
                .when(provisionOrganization).execute(anyString(), any());

        assertThatCode(() -> loaderOver(resolverFor(yaml(FILE, "name: X\n"))).loadDefaults())
                .doesNotThrowAnyException();
    }

    /**
     * Load-bearing, and invisible at run-time until something breaks: every feature's seed file is
     * named after a tenant and now skips one that does not exist, so this loader has to have created
     * it first. base-entity's importer is {@code @Order(10)} and base-state's {@code @Order(20)};
     * anything unannotated sorts after both.
     */
    @Test
    void runsBeforeEveryFeatureSeedLoader() throws NoSuchMethodException {
        Order order = DefaultOrganizationLoader.class.getMethod("loadDefaults").getAnnotation(Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isLessThan(10);
    }

    private DefaultOrganizationLoader loaderOver(ResourcePatternResolver resolver) {
        return new DefaultOrganizationLoader(provisionOrganization, repository, resolver);
    }

    private static ResourcePatternResolver resolverFor(Resource... resources) {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        try {
            when(resolver.getResources(anyString())).thenReturn(resources);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return resolver;
    }

    /** An in-memory YAML file; {@link ByteArrayResource} has no name of its own. */
    private static Resource yaml(String fileName, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }
}
