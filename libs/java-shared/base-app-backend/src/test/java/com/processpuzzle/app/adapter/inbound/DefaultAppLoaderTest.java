package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.model.AppDefinition;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.KeyAvailability;
import com.processpuzzle.app.model.ModuleDefinition;
import com.processpuzzle.app.model.ModuleDefinitionInput;
import com.processpuzzle.app.model.Organization;
import com.processpuzzle.app.model.OrganizationInput;
import com.processpuzzle.app.model.OrganizationStatus;
import com.processpuzzle.app.model.RouteDefinition;
import com.processpuzzle.app.model.ProvisioningResult;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.shared.model.WidgetInstance;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.exception.AppDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionInvalidException;
import com.processpuzzle.app.usecase.port.EntityNameRegistry;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.app.usecase.service.AppRuleValidator;
import com.processpuzzle.rule.usecase.EvaluateObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the loader's file walk and, through the bundled {@code processpuzzle-testbed-apps.yaml},
 * the shipped default definition itself: the last test feeds the parsed demo app to the real
 * {@link AppDefinitionValidator}, so a YAML edit that breaks a route or nav reference fails here
 * rather than at run-time with the loader logging a rejection nobody reads.
 */
class DefaultAppLoaderTest {

    private static final String TESTBED_KEY = "processpuzzle-testbed";
    private static final String TESTBED_FILE = "processpuzzle-testbed-apps.yaml";

    private AppEndpoint endpoint;
    private ResourcePatternResolver resourceResolver;
    private DefaultAppLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        endpoint = mock(AppEndpoint.class);
        resourceResolver = mock(ResourcePatternResolver.class);
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] { bundledTestbedFile() });
        keyIs(availableKey(TESTBED_KEY));
        when(endpoint.provisionOrganization(any())).thenAnswer(call -> provisioned(call.getArgument(0)));
        when(endpoint.createAppDefinition(anyString(), any())).thenAnswer(call -> created(call.getArgument(1)));
        when(endpoint.createModuleDefinition(anyString(), any())).thenAnswer(call -> createdModule(call.getArgument(1)));
        loader = new DefaultAppLoader(endpoint, resourceResolver);
    }

    @Test
    void provisionsTheOrganizationNamedByTheFileAndCreatesItsDefinitions() {
        loader.loadDefaults();

        ArgumentCaptor<OrganizationInput> organization = ArgumentCaptor.forClass(OrganizationInput.class);
        verify(endpoint).provisionOrganization(organization.capture());
        assertThat(organization.getValue().getKey()).isEqualTo(TESTBED_KEY);
        assertThat(organization.getValue().getName()).isEqualTo("ProcessPuzzle Testbed");
        assertThat(organization.getValue().getDefaultLocale()).isEqualTo("en");

        verify(endpoint).createAppDefinition(eq(TESTBED_KEY), any(AppDefinitionInput.class));
    }

    @Test
    void loadsIntoAnExistingOrganizationInsteadOfReprovisioningIt() {
        keyIs(takenKey(TESTBED_KEY));

        loader.loadDefaults();

        verify(endpoint, never()).provisionOrganization(any());
        verify(endpoint).createAppDefinition(eq(TESTBED_KEY), any(AppDefinitionInput.class));
    }

    @Test
    void leavesAnAlreadyPresentDefinitionUntouched() {
        // doThrow, not when(...).thenThrow: re-stubbing through the mock would invoke the answer set up
        // in setUp with null arguments.
        doThrow(new AppDefinitionAlreadyExistsException(TESTBED_KEY, "demo"))
                .when(endpoint).createAppDefinition(anyString(), any());

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void survivesADefinitionRejectedByValidation() {
        doThrow(new AppDefinitionInvalidException(TESTBED_KEY, "demo",
                List.of(new AppValidationProblem("/routes/0", "app.validation.orphan-route", "Unreachable."))))
                .when(endpoint).createAppDefinition(anyString(), any());

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void skipsAFileWhoseKeyIsNotClaimable() {
        keyIs(reservedKey("api"));
        resolvesTo(yamlFile("api-apps.yaml", "appDefinitions:\n  - id: demo\n    name: Demo\n"));

        loader.loadDefaults();

        verify(endpoint, never()).provisionOrganization(any());
        verify(endpoint, never()).createAppDefinition(anyString(), any());
    }

    @Test
    void skipsAFileNotNamedAfterAnOrganization() {
        resolvesTo(yamlFile("apps.yaml", "appDefinitions:\n  - id: demo\n    name: Demo\n"));

        loader.loadDefaults();

        verify(endpoint, never()).checkOrganizationKey(anyString());
        verify(endpoint, never()).createAppDefinition(anyString(), any());
    }

    @Test
    void provisionsAFileWithoutAnOrganizationBlockUnderItsOwnKey() {
        keyIs(availableKey("other-org"));
        resolvesTo(yamlFile("other-org-apps.yaml", "appDefinitions:\n  - id: demo\n    name: Demo\n"));

        loader.loadDefaults();

        ArgumentCaptor<OrganizationInput> organization = ArgumentCaptor.forClass(OrganizationInput.class);
        verify(endpoint).provisionOrganization(organization.capture());
        assertThat(organization.getValue().getKey()).isEqualTo("other-org");
        assertThat(organization.getValue().getName()).isEqualTo("other-org");
    }

    @Test
    void theBundledDemoAppIsAValidNavigableDefinition() {
        loader.loadDefaults();

        AppDefinitionInput demo = capturedDefinition();
        assertThat(demo.getId()).isEqualTo("demo");
        assertThat(demo.getName()).isEqualTo("Demo");
        assertThat(demo.getTranslocoId()).isEqualTo("demo.app.name");
        assertThat(demo.getTheme()).isNotNull();
        assertThat(demo.getLayout()).isNotNull();

        // Every region type is declared, and the sidenav is populated — an app published without one
        // gives end users a shell they cannot navigate.
        assertThat(demo.getRegions()).extracting(RegionDefinition::getType)
                .containsExactly(RegionType.HEADER, RegionType.SIDENAV, RegionType.FOOTER);
        assertThat(sidenavOf(demo).getNavItems()).isNotEmpty();

        // Every declared route is reachable and every entity widget names its entity, both of which the
        // structural validator and the 'App Definition' rules require.
        assertThat(demo.getRoutes()).extracting(RouteDefinition::getPath)
                .containsExactly("order-list", "order-entry", "order-line-list");
        assertThat(demo.getRoutes()).allSatisfy(route ->
                assertThat(route.getTarget().getWidgets()).isNotEmpty().allSatisfy(widget ->
                        assertThat(entityNameOf(widget)).isNotBlank()));

        // Nothing blocking, and exactly one advisory: 'nav-order-admin' points into the mounted module,
        // whose routes an app definition cannot see. That warning is the loose coupling working, so it is
        // asserted rather than tolerated — a second one would mean a genuinely broken reference.
        List<AppValidationProblem> problems = structuralValidator().validate(TESTBED_KEY, demo);
        assertThat(problems).noneMatch(AppValidationProblem::blocksPersisting);
        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.errorId()).isEqualTo("app.validation.unknown-route-reference");
            assertThat(problem.path()).endsWith("/routePath");
        });
    }

    /**
     * The bundled module, and the seeding order it relies on: a mount in the same file names a module
     * that exists by the time the app is created. Nothing breaks if it does not — a dangling mount is a
     * warning — but a fresh startup should not log one the file itself answers.
     */
    @Test
    void theBundledModuleIsCreatedBeforeTheAppThatMountsIt() {
        loader.loadDefaults();

        InOrder order = inOrder(endpoint);
        order.verify(endpoint).createModuleDefinition(eq(TESTBED_KEY), any(ModuleDefinitionInput.class));
        order.verify(endpoint).createAppDefinition(eq(TESTBED_KEY), any(AppDefinitionInput.class));

        ModuleDefinitionInput module = capturedModule();
        assertThat(module.getKey()).isEqualTo("order-admin");
        assertThat(module.getRoutes()).extracting(RouteDefinition::getPath).containsExactly("lines", "line/:id");
        assertThat(structuralValidator().validateModule(TESTBED_KEY, module)).isEmpty();

        // The mount is what makes the module reachable, and it is the one the nav item points into.
        assertThat(capturedDefinition().getModules()).singleElement().satisfies(mount -> {
            assertThat(mount.getModuleKey()).isEqualTo("order-admin");
            assertThat(mount.getBasePath()).isEqualTo("back-office");
        });
    }

    @Test
    void leavesAnAlreadyPresentModuleUntouchedAndStillLoadsTheApps() {
        doThrow(new ModuleDefinitionAlreadyExistsException(TESTBED_KEY, "order-admin"))
                .when(endpoint).createModuleDefinition(anyString(), any());

        loader.loadDefaults();

        verify(endpoint).createAppDefinition(eq(TESTBED_KEY), any(AppDefinitionInput.class));
    }

    /** A rejected module leaves a dangling mount, which is a warning — so the apps still load. */
    @Test
    void survivesAModuleRejectedByValidation() {
        doThrow(new ModuleDefinitionInvalidException(TESTBED_KEY, "order-admin",
                List.of(new AppValidationProblem("/routes/0", "app.validation.route-path-missing", "No path."))))
                .when(endpoint).createModuleDefinition(anyString(), any());

        loader.loadDefaults();

        verify(endpoint).createAppDefinition(eq(TESTBED_KEY), any(AppDefinitionInput.class));
    }

    @Test
    void aModuleCreationFailingForAnyOtherReason_isSurvived() {
        doThrow(new IllegalStateException("constraint violation"))
                .when(endpoint).createModuleDefinition(anyString(), any());

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        verify(endpoint).createAppDefinition(eq(TESTBED_KEY), any(AppDefinitionInput.class));
    }

    /** The revision is only logged, so an answer without one must not become an exception. */
    @Test
    void aModuleCreationAnsweringWithoutARevision_isSurvived() {
        doReturn(new ResponseEntity<>(new ModuleDefinition(), HttpStatus.CREATED))
                .when(endpoint).createModuleDefinition(anyString(), any());
        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        doReturn(ResponseEntity.ok(null)).when(endpoint).createModuleDefinition(anyString(), any());
        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    /** A blank key is as unusable as an absent one: the key is how a mount names the module. */
    @Test
    void aModuleWithoutAUsableKey_isRejectedWithoutReachingTheEndpoint() {
        resolvesTo(yamlFile("other-org-apps.yaml",
                "moduleDefinitions:\n  - name: Nameless\n  - key: \"   \"\n    name: Blank\n"));
        keyIs(availableKey("other-org"));

        loader.loadDefaults();

        verify(endpoint, never()).createModuleDefinition(anyString(), any());
    }

    @Test
    void aFileDeclaringNoModules_seedsNone() {
        resolvesTo(yamlFile("other-org-apps.yaml", "appDefinitions:\n  - id: demo\n    name: Demo\n"));
        keyIs(availableKey("other-org"));

        loader.loadDefaults();

        verify(endpoint, never()).createModuleDefinition(anyString(), any());
    }

    /**
     * A convenience that refuses to boot would be worse than one that seeds nothing, so every failure
     * below has to leave the application started.
     */
    @Test
    void anUnscannableClasspath_isLoggedRatherThanFailingStartup() throws IOException {
        when(resourceResolver.getResources(anyString())).thenThrow(new IOException("no such classpath"));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        verify(endpoint, never()).checkOrganizationKey(anyString());
    }

    @Test
    void aDeploymentBundlingNoDefaults_seedsNothing() {
        resolvesTo();

        loader.loadDefaults();

        verify(endpoint, never()).checkOrganizationKey(anyString());
        verify(endpoint, never()).createAppDefinition(anyString(), any());
    }

    @Test
    void anUnreadableFile_isSkippedWithoutTouchingTheTenant() {
        resolvesTo(yamlFile("broken-apps.yaml", "appDefinitions: [\n"));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        verify(endpoint, never()).provisionOrganization(any());
        verify(endpoint, never()).createAppDefinition(anyString(), any());
    }

    @Test
    void aFailingKeyCheck_skipsTheFile() {
        when(endpoint.checkOrganizationKey(anyString())).thenThrow(new IllegalStateException("no database"));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        verify(endpoint, never()).createAppDefinition(anyString(), any());
    }

    @Test
    void aKeyCheckThatAnswersNothing_skipsTheFile() {
        when(endpoint.checkOrganizationKey(anyString())).thenReturn(ResponseEntity.ok(null));

        loader.loadDefaults();

        verify(endpoint, never()).createAppDefinition(anyString(), any());
    }

    @Test
    void aFailingProvisioning_skipsTheFile() {
        doThrow(new IllegalStateException("no database")).when(endpoint).provisionOrganization(any());

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        verify(endpoint, never()).createAppDefinition(anyString(), any());
    }

    /** Provisioning answering without a starter app is odd but not a reason to stop loading. */
    @Test
    void provisioningThatAnswersNoStarterApp_stillLoadsTheDefinitions() {
        doReturn(new ResponseEntity<>(new ProvisioningResult(), HttpStatus.CREATED))
                .when(endpoint).provisionOrganization(any());
        loader.loadDefaults();

        doReturn(ResponseEntity.ok(null)).when(endpoint).provisionOrganization(any());
        loader.loadDefaults();

        verify(endpoint, times(2)).createAppDefinition(eq(TESTBED_KEY), any(AppDefinitionInput.class));
    }

    /** A blank id is as unusable as an absent one: the id is the app's route path segment. */
    @Test
    void anEntryWithoutAUsableId_isRejectedWithoutReachingTheEndpoint() {
        resolvesTo(yamlFile("other-org-apps.yaml",
                "appDefinitions:\n  - name: Nameless\n  - id: \"   \"\n    name: Blank\n"));
        keyIs(availableKey("other-org"));

        loader.loadDefaults();

        verify(endpoint, never()).createAppDefinition(anyString(), any());
    }

    /** An organization block that names nothing still has to yield a provisionable payload. */
    @Test
    void aFileWithAnUnnamedTenantAndNoDefinitions_provisionsUnderItsOwnKey() {
        resolvesTo(yamlFile("other-org-apps.yaml", "organization:\n  description: Only a description.\n"));
        keyIs(availableKey("other-org"));

        loader.loadDefaults();

        ArgumentCaptor<OrganizationInput> organization = ArgumentCaptor.forClass(OrganizationInput.class);
        verify(endpoint).provisionOrganization(organization.capture());
        assertThat(organization.getValue().getName()).isEqualTo("other-org");
        assertThat(organization.getValue().getDescription()).isEqualTo("Only a description.");
        verify(endpoint, never()).createAppDefinition(anyString(), any());
    }

    @Test
    void aCreationFailingForAnyOtherReason_isSurvived() {
        doThrow(new IllegalStateException("constraint violation"))
                .when(endpoint).createAppDefinition(anyString(), any());

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    /** The revision is only logged, so an answer without one must not become an exception. */
    @Test
    void aCreationAnsweringWithoutARevision_isSurvived() {
        doReturn(new ResponseEntity<>(new AppDefinition(), HttpStatus.CREATED))
                .when(endpoint).createAppDefinition(anyString(), any());
        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        doReturn(ResponseEntity.ok(null)).when(endpoint).createAppDefinition(anyString(), any());
        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void aResourceWithoutAName_isSkipped() {
        resolvesTo(new ByteArrayResource("appDefinitions: []".getBytes(StandardCharsets.UTF_8)));

        loader.loadDefaults();

        verify(endpoint, never()).checkOrganizationKey(anyString());
    }

    /** {@code -apps.yaml} on its own names no organization, so there is nowhere to put its contents. */
    @Test
    void aFileNamedOnlyAfterTheSuffix_isSkipped() {
        resolvesTo(yamlFile("-apps.yaml", "appDefinitions: []"));

        loader.loadDefaults();

        verify(endpoint, never()).checkOrganizationKey(anyString());
    }

    // --- helpers -----------------------------------------------------------------------------

    private AppDefinitionInput capturedDefinition() {
        ArgumentCaptor<AppDefinitionInput> definition = ArgumentCaptor.forClass(AppDefinitionInput.class);
        verify(endpoint).createAppDefinition(eq(TESTBED_KEY), definition.capture());
        return definition.getValue();
    }

    private ModuleDefinitionInput capturedModule() {
        ArgumentCaptor<ModuleDefinitionInput> module = ArgumentCaptor.forClass(ModuleDefinitionInput.class);
        verify(endpoint).createModuleDefinition(eq(TESTBED_KEY), module.capture());
        return module.getValue();
    }

    private static RegionDefinition sidenavOf(AppDefinitionInput definition) {
        Optional<RegionDefinition> sidenav = definition.getRegions().stream()
                .filter(region -> region.getType() == RegionType.SIDENAV).findFirst();
        assertThat(sidenav).isPresent();
        return sidenav.get();
    }

    private static String entityNameOf(WidgetInstance widget) {
        assertThat(widget.getProps()).isNotNull();
        return String.valueOf(widget.getProps().get("entityName"));
    }

    /** The real structural validator, with no entity registry and no rule engine wired. */
    @SuppressWarnings("unchecked")
    private static AppDefinitionValidator structuralValidator() {
        ObjectProvider<EntityNameRegistry> entityRegistryProvider = mock(ObjectProvider.class);
        when(entityRegistryProvider.getIfAvailable()).thenReturn(null);
        ObjectProvider<EvaluateObject> evaluateObjectProvider = mock(ObjectProvider.class);
        when(evaluateObjectProvider.getIfAvailable()).thenReturn(null);
        return new AppDefinitionValidator(entityRegistryProvider, new AppRuleValidator(evaluateObjectProvider));
    }

    private void keyIs(KeyAvailability availability) {
        when(endpoint.checkOrganizationKey(anyString())).thenReturn(ResponseEntity.ok(availability));
    }

    private void resolvesTo(Resource... resources) {
        try {
            when(resourceResolver.getResources(anyString())).thenReturn(resources);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static KeyAvailability availableKey(String key) {
        return new KeyAvailability(key, true);
    }

    private static KeyAvailability takenKey(String key) {
        KeyAvailability availability = new KeyAvailability(key, false);
        availability.setErrorId("organization.key.taken");
        return availability;
    }

    private static KeyAvailability reservedKey(String key) {
        KeyAvailability availability = new KeyAvailability(key, false);
        availability.setErrorId("organization.key.reserved");
        return availability;
    }

    private static ResponseEntity<ProvisioningResult> provisioned(OrganizationInput input) {
        Organization organization = new Organization(input.getKey(), input.getName(), OrganizationStatus.ACTIVE);
        AppDefinition starter = new AppDefinition();
        starter.setId("app");
        return new ResponseEntity<>(new ProvisioningResult(organization, starter), HttpStatus.CREATED);
    }

    private static ResponseEntity<ModuleDefinition> createdModule(ModuleDefinitionInput input) {
        ModuleDefinition definition = new ModuleDefinition();
        definition.setKey(input.getKey());
        definition.setVersion(1L);
        return new ResponseEntity<>(definition, HttpStatus.CREATED);
    }

    private static ResponseEntity<AppDefinition> created(AppDefinitionInput input) {
        AppDefinition definition = new AppDefinition();
        definition.setId(input.getId());
        definition.setVersion(1L);
        return new ResponseEntity<>(definition, HttpStatus.CREATED);
    }

    /** The file this library actually ships, so the test reads the same bytes production does. */
    private static Resource bundledTestbedFile() {
        return new ClassPathResource("default-apps/" + TESTBED_FILE);
    }

    /** An in-memory YAML file; {@link ByteArrayResource} has no name of its own. */
    private static Resource yamlFile(String fileName, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }
}
