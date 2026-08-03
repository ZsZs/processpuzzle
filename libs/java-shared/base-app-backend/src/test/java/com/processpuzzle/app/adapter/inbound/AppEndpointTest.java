package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.Organization;
import com.processpuzzle.app.domain.OrganizationStatus;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.AppDefinitionStatus;
import com.processpuzzle.app.model.KeyAvailability;
import com.processpuzzle.app.model.OrganizationInput;
import com.processpuzzle.app.model.OrganizationUpdate;
import com.processpuzzle.app.model.PageDefinition;
import com.processpuzzle.app.model.PageOfAppDefinition;
import com.processpuzzle.app.model.ProvisioningResult;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.model.ValidationResult;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.CheckOrganizationKey;
import com.processpuzzle.app.usecase.CreateAppDefinition;
import com.processpuzzle.app.usecase.DeleteAppDefinition;
import com.processpuzzle.app.usecase.DeleteOrganization;
import com.processpuzzle.app.usecase.ExportAppDefinition;
import com.processpuzzle.app.usecase.FindAllAppDefinitions;
import com.processpuzzle.app.usecase.FindAppDefinition;
import com.processpuzzle.app.usecase.FindOrganization;
import com.processpuzzle.app.usecase.GetAppLayout;
import com.processpuzzle.app.usecase.GetPageDefinition;
import com.processpuzzle.app.usecase.ImportAppDefinitions;
import com.processpuzzle.app.usecase.ImportOutcome;
import com.processpuzzle.app.usecase.KeyCheckOutcome;
import com.processpuzzle.app.usecase.ProvisionOrganization;
import com.processpuzzle.app.usecase.PublishAppDefinition;
import com.processpuzzle.app.usecase.UpdateAppDefinition;
import com.processpuzzle.app.usecase.UpdateOrganization;
import com.processpuzzle.app.usecase.ValidateAppDefinition;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.shared.model.ImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.processpuzzle.app.AppTestFixtures.APP_ID;
import static com.processpuzzle.app.AppTestFixtures.ORG_KEY;
import static com.processpuzzle.app.AppTestFixtures.PAGE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The adapter holds no logic, so what is worth checking is exactly what a delegating adapter can
 * still get wrong: the status code a creation answers with, the {@code draft} query parameter's
 * default when it is absent, the content type of a file download, and turning a checked
 * {@link IOException} into something the global advice can render.
 */
class AppEndpointTest {

    private ProvisionOrganization provisionOrganization;
    private CheckOrganizationKey checkOrganizationKey;
    private FindOrganization findOrganization;
    private UpdateOrganization updateOrganization;
    private DeleteOrganization deleteOrganization;
    private CreateAppDefinition createAppDefinition;
    private FindAppDefinition findAppDefinition;
    private FindAllAppDefinitions findAllAppDefinitions;
    private UpdateAppDefinition updateAppDefinition;
    private DeleteAppDefinition deleteAppDefinition;
    private PublishAppDefinition publishAppDefinition;
    private GetAppLayout getAppLayout;
    private GetPageDefinition getPageDefinition;
    private ValidateAppDefinition validateAppDefinition;
    private ImportAppDefinitions importAppDefinitions;
    private ExportAppDefinition exportAppDefinition;
    private AppEndpoint endpoint;

    @BeforeEach
    void setUp() {
        provisionOrganization = mock(ProvisionOrganization.class);
        checkOrganizationKey = mock(CheckOrganizationKey.class);
        findOrganization = mock(FindOrganization.class);
        updateOrganization = mock(UpdateOrganization.class);
        deleteOrganization = mock(DeleteOrganization.class);
        createAppDefinition = mock(CreateAppDefinition.class);
        findAppDefinition = mock(FindAppDefinition.class);
        findAllAppDefinitions = mock(FindAllAppDefinitions.class);
        updateAppDefinition = mock(UpdateAppDefinition.class);
        deleteAppDefinition = mock(DeleteAppDefinition.class);
        publishAppDefinition = mock(PublishAppDefinition.class);
        getAppLayout = mock(GetAppLayout.class);
        getPageDefinition = mock(GetPageDefinition.class);
        validateAppDefinition = mock(ValidateAppDefinition.class);
        importAppDefinitions = mock(ImportAppDefinitions.class);
        exportAppDefinition = mock(ExportAppDefinition.class);

        endpoint = new AppEndpoint(provisionOrganization, checkOrganizationKey, findOrganization,
                updateOrganization, deleteOrganization, createAppDefinition, findAppDefinition,
                findAllAppDefinitions, updateAppDefinition, deleteAppDefinition, publishAppDefinition,
                getAppLayout, getPageDefinition, validateAppDefinition, importAppDefinitions,
                exportAppDefinition, new AppMapper());
    }

    // --- organizations -------------------------------------------------------------------

    @Test
    void provisioningAnOrganizationAnswers201WithTheTenantAndItsStarterApp() {
        when(provisionOrganization.execute(any())).thenReturn(
                new ProvisionOrganization.Result(organization(), AppTestFixtures.storedDefinition()));

        ResponseEntity<ProvisioningResult> response =
                endpoint.provisionOrganization(new OrganizationInput(ORG_KEY, "My Organization Ltd."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrganization().getKey()).isEqualTo(ORG_KEY);
        assertThat(response.getBody().getAppDefinition().getId()).isEqualTo(APP_ID);
    }

    @Test
    void checkingAKeyAnswersTheOutcomeIncludingItsSuggestions() {
        when(checkOrganizationKey.execute("my-org")).thenReturn(
                KeyCheckOutcome.unavailable("my-org", "organization.key.taken", List.of("my-org-1")));

        ResponseEntity<KeyAvailability> response = endpoint.checkOrganizationKey("my-org");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAvailable()).isFalse();
        assertThat(response.getBody().getErrorId()).isEqualTo("organization.key.taken");
        assertThat(response.getBody().getSuggestions()).containsExactly("my-org-1");
    }

    @Test
    void readingAnOrganizationAnswersItsModel() {
        when(findOrganization.execute(ORG_KEY)).thenReturn(organization());

        assertThat(endpoint.getOrganization(ORG_KEY).getBody()).isNotNull()
                .satisfies(model -> assertThat(model.getContactEmail()).isEqualTo("ops@my-org.example"));
    }

    @Test
    void updatingAnOrganizationForwardsThePayloadAndAnswersTheUpdatedModel() {
        OrganizationUpdate input = new OrganizationUpdate("My Organization GmbH");
        when(updateOrganization.execute(ORG_KEY, input)).thenReturn(organization());

        assertThat(endpoint.updateOrganization(ORG_KEY, input).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(updateOrganization).execute(ORG_KEY, input);
    }

    @Test
    void deletingAnOrganizationAnswers204WithNoBody() {
        ResponseEntity<Void> response = endpoint.deleteOrganization(ORG_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(deleteOrganization).execute(ORG_KEY);
    }

    // --- app definitions -----------------------------------------------------------------

    @Test
    void creatingADefinitionAnswers201() {
        AppDefinitionInput input = AppTestFixtures.validInput(APP_ID);
        when(createAppDefinition.execute(ORG_KEY, input)).thenReturn(AppTestFixtures.storedDefinition());

        ResponseEntity<com.processpuzzle.app.model.AppDefinition> response =
                endpoint.createAppDefinition(ORG_KEY, input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(AppDefinitionStatus.DRAFT);
    }

    @Test
    void readingADefinitionAnswersTheWholeGraph() {
        when(findAppDefinition.execute(ORG_KEY, APP_ID)).thenReturn(AppTestFixtures.storedDefinition());

        com.processpuzzle.app.model.AppDefinition body = endpoint.getAppDefinition(ORG_KEY, APP_ID).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getPages()).extracting(PageDefinition::getId).containsExactly(PAGE_ID);
        assertThat(body.getRegions()).extracting(region -> region.getType())
                .containsExactly(RegionType.SIDENAV);
    }

    @Test
    void listingDefinitionsForwardsEveryQueryParameterAndAnswersAPagedDefinition() {
        when(findAllAppDefinitions.execute(ORG_KEY, "id==claims-app", "name,asc", 2, 10))
                .thenReturn(new PageImpl<>(List.of(AppTestFixtures.storedDefinition())));

        ResponseEntity<PageOfAppDefinition> response =
                endpoint.listAppDefinitions(ORG_KEY, "id==claims-app", "name,asc", 2, 10);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).singleElement()
                .satisfies(definition -> assertThat(definition.getId()).isEqualTo(APP_ID));
        assertThat(response.getBody().getTotalElements()).isEqualTo(1L);
        verify(findAllAppDefinitions).execute(ORG_KEY, "id==claims-app", "name,asc", 2, 10);
    }

    /**
     * The designer edits a definition straight out of the list instead of re-fetching it by id, so
     * a list entry that dropped the graph would be silently written back as an empty one by the
     * next full-replacement PUT.
     */
    @Test
    void aListedDefinitionCarriesTheWholeGraphRatherThanHeaderFieldsOnly() {
        when(findAllAppDefinitions.execute(ORG_KEY, null, null, null, null))
                .thenReturn(new PageImpl<>(List.of(AppTestFixtures.storedDefinition())));

        ResponseEntity<PageOfAppDefinition> response =
                endpoint.listAppDefinitions(ORG_KEY, null, null, null, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).singleElement().satisfies(definition -> {
            assertThat(definition.getRegions()).extracting(RegionDefinition::getType)
                    .containsExactly(RegionType.SIDENAV);
            assertThat(definition.getPages()).extracting(PageDefinition::getId).containsExactly(PAGE_ID);
        });
    }

    @Test
    void updatingADefinitionAnswers200() {
        AppDefinitionInput input = AppTestFixtures.validInput(APP_ID);
        when(updateAppDefinition.execute(ORG_KEY, APP_ID, input))
                .thenReturn(AppTestFixtures.storedDefinition());

        assertThat(endpoint.updateAppDefinition(ORG_KEY, APP_ID, input).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void deletingADefinitionAnswers204() {
        assertThat(endpoint.deleteAppDefinition(ORG_KEY, APP_ID).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteAppDefinition).execute(ORG_KEY, APP_ID);
    }

    @Test
    void publishingADefinitionAnswersItAsPublished() {
        AppDefinition published = AppTestFixtures.storedDefinition();
        published.publish();
        when(publishAppDefinition.execute(ORG_KEY, APP_ID)).thenReturn(published);

        assertThat(endpoint.publishAppDefinition(ORG_KEY, APP_ID).getBody()).isNotNull()
                .satisfies(body -> assertThat(body.getStatus()).isEqualTo(AppDefinitionStatus.PUBLISHED));
    }

    // --- runtime -------------------------------------------------------------------------

    @Test
    void theLayoutCarriesTheOrganizationsDefaultLocaleAlongsideTheGraph() {
        AppDefinition definition = AppTestFixtures.storedDefinition();
        when(getAppLayout.execute(ORG_KEY, APP_ID, false)).thenReturn(
                new GetAppLayout.Result(definition, definition.getDraftGraph(), "en-GB"));

        assertThat(endpoint.getAppLayout(ORG_KEY, APP_ID, null).getBody()).isNotNull()
                .satisfies(layout -> {
                    assertThat(layout.getOrgKey()).isEqualTo(ORG_KEY);
                    assertThat(layout.getDefaultLocale()).isEqualTo("en-GB");
                    assertThat(layout.getRegions()).hasSize(1);
                });
    }

    /**
     * {@code draft} is an optional query parameter, so it arrives as {@code null} on every run-time
     * request. Defaulting it to anything but {@code false} would serve unpublished edits to end users.
     */
    @Test
    void anAbsentDraftParameterMeansThePublishedRevision() {
        AppDefinition definition = AppTestFixtures.storedDefinition();
        when(getAppLayout.execute(anyString(), anyString(), anyBoolean())).thenReturn(
                new GetAppLayout.Result(definition, definition.getDraftGraph(), null));
        when(getPageDefinition.execute(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(definition.getDraftGraph().pages().getFirst());

        endpoint.getAppLayout(ORG_KEY, APP_ID, null);
        endpoint.getPageDefinition(ORG_KEY, APP_ID, PAGE_ID, null);
        endpoint.getAppLayout(ORG_KEY, APP_ID, true);
        endpoint.getPageDefinition(ORG_KEY, APP_ID, PAGE_ID, true);

        verify(getAppLayout).execute(ORG_KEY, APP_ID, false);
        verify(getPageDefinition).execute(ORG_KEY, APP_ID, PAGE_ID, false);
        verify(getAppLayout).execute(ORG_KEY, APP_ID, true);
        verify(getPageDefinition).execute(ORG_KEY, APP_ID, PAGE_ID, true);
    }

    @Test
    void readingAPageAnswersItsWidgets() {
        AppDefinition definition = AppTestFixtures.storedDefinition();
        when(getPageDefinition.execute(ORG_KEY, APP_ID, PAGE_ID, false))
                .thenReturn(definition.getDraftGraph().pages().getFirst());

        assertThat(endpoint.getPageDefinition(ORG_KEY, APP_ID, PAGE_ID, false).getBody()).isNotNull()
                .satisfies(page -> assertThat(page.getId()).isEqualTo(PAGE_ID));
    }

    // --- validation and transfer ---------------------------------------------------------

    /** A definition carrying only advice is valid, so {@code valid} is not "the list is empty". */
    @Test
    void validationAnswersTheProblemsAndWhetherTheyBlockAWrite() {
        when(validateAppDefinition.execute(eq(ORG_KEY), any())).thenReturn(List.of(
                new AppValidationProblem("/", "rule.appDefinition.hasNavigation",
                        "This app declares no sidenav navigation.", Severity.WARNING)));

        ValidationResult result =
                endpoint.validateAppDefinition(ORG_KEY, AppTestFixtures.validInput(APP_ID)).getBody();

        assertThat(result).isNotNull();
        assertThat(result.getValid()).isTrue();
        assertThat(result.getProblems()).singleElement().satisfies(problem -> {
            assertThat(problem.getErrorId()).isEqualTo("rule.appDefinition.hasNavigation");
            assertThat(problem.getSeverity()).isEqualTo(com.processpuzzle.app.model.Severity.WARNING);
        });
    }

    @Test
    void importingForwardsTheUploadedStreamAndAnswersTheCounters() throws IOException {
        when(importAppDefinitions.execute(eq(ORG_KEY), any(InputStream.class)))
                .thenReturn(new ImportOutcome(2, 1, List.of()));

        ImportResult result = endpoint.importAppDefinitions(ORG_KEY, upload("appDefinitions: []")).getBody();

        assertThat(result).isNotNull();
        assertThat(result.getCreated()).isEqualTo(2);
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void anUnreadableUpload_surfacesAsAnUncheckedIoException() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("stream closed"));

        assertThatThrownBy(() -> endpoint.importAppDefinitions(ORG_KEY, file))
                .isInstanceOf(UncheckedIOException.class)
                .hasRootCauseMessage("stream closed");
    }

    /**
     * The generated operation also declares {@code application/json}, so without an explicit content
     * type content negotiation picks JSON and serializes the byte array instead of downloading it.
     */
    @Test
    void exportingAnswersAYamlAttachmentNamedAfterTheTenantAndTheApp() throws IOException {
        byte[] yaml = "appDefinitions: []\n".getBytes(StandardCharsets.UTF_8);
        when(exportAppDefinition.execute(ORG_KEY, APP_ID)).thenReturn(yaml);

        ResponseEntity<Resource> response = endpoint.exportAppDefinition(ORG_KEY, APP_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"" + ORG_KEY + "-" + APP_ID + ".yaml\"");
        assertThat(response.getHeaders().getContentType()).hasToString("application/x-yaml");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContentAsByteArray()).isEqualTo(yaml);
    }

    @Test
    void aFailingExport_surfacesAsAnUncheckedIoException() throws IOException {
        when(exportAppDefinition.execute(ORG_KEY, APP_ID)).thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> endpoint.exportAppDefinition(ORG_KEY, APP_ID))
                .isInstanceOf(UncheckedIOException.class)
                .hasRootCauseMessage("disk full");
    }

    @Test
    void listingWithoutAnyQueryParameterForwardsNulls() {
        when(findAllAppDefinitions.execute(eq(ORG_KEY), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(endpoint.listAppDefinitions(ORG_KEY, null, null, null, null).getBody())
                .isNotNull()
                .satisfies(body -> assertThat(body.getContent()).isEmpty());
    }

    private static MultipartFile upload(String content) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream())
                .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        return file;
    }

    private static Organization organization() {
        return new Organization(ORG_KEY, "My Organization Ltd.", "Insurance.", "ops@my-org.example",
                "en-GB", OrganizationStatus.ACTIVE);
    }
}
