package com.processpuzzle.app.usecase;

import com.processpuzzle.app.usecase.port.TenantDirectory;
import org.springframework.beans.factory.ObjectProvider;
import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.exception.UnknownTenantException;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.app.usecase.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static com.processpuzzle.app.AppTestFixtures.APP_ID;
import static com.processpuzzle.app.AppTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The two properties an import has to hold to be usable at all: it is <em>all-or-nothing</em>, so a
 * file with one bad entry leaves the tenant exactly as it was and reports every problem at once; and
 * it produces <em>drafts</em>, so importing into a live tenant changes nothing end users see until
 * someone publishes.
 */
class ImportAppDefinitionsTest {

    private static final String VALID_FILE = """
            appDefinitions:
              - id: claims-app
                name: Claims Management
                translocoId: claims.app.name
                regions:
                  - type: sidenav
                    navItems:
                      - id: nav-claims
                        label: Claims
                        routePath: claims-list
                routes:
                  - path: claims-list
                    title: Claims
            """;

    private AppDefinitionRepository repository;
    private ObjectProvider<TenantDirectory> tenantDirectory;
    private ImportAppDefinitions importAppDefinitions;

    @BeforeEach
    void setUp() {
        repository = mock(AppDefinitionRepository.class);
        when(repository.save(any(AppDefinition.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.findByOrgKeyAndId(anyString(), anyString())).thenReturn(Optional.empty());

        tenantDirectory = AppTestFixtures.tenantDirectory(ORG_KEY);

        importAppDefinitions = new ImportAppDefinitions(repository, tenantDirectory,
                AppTestFixtures.structuralValidator(), AppTestFixtures.permissiveGuard(), new AppMapper());
    }

    @Test
    void createsADefinitionThatIsNotYetInTheTenant() throws IOException {
        ImportOutcome outcome = importAppDefinitions.execute(ORG_KEY, yaml(VALID_FILE));

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.errors()).isEmpty();

        AppDefinition saved = captureSaved();
        assertThat(saved.getOrgKey()).isEqualTo(ORG_KEY);
        assertThat(saved.getId()).isEqualTo(APP_ID);
        assertThat(saved.getTranslocoId()).isEqualTo("claims.app.name");
        assertThat(saved.getDraftGraph().routes()).hasSize(1);
        assertThat(saved.getRevision()).isEqualTo(1L);
    }

    /** An import advances the draft revision but must leave the published snapshot alone. */
    @Test
    void replacesAnExistingDefinitionsDraftWithoutTouchingWhatIsPublished() throws IOException {
        AppDefinition existing = AppTestFixtures.storedDefinition();
        existing.publish();
        when(repository.findByOrgKeyAndId(ORG_KEY, APP_ID)).thenReturn(Optional.of(existing));

        ImportOutcome outcome = importAppDefinitions.execute(ORG_KEY, yaml(VALID_FILE));

        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isEqualTo(1);
        assertThat(existing.getRevision()).isEqualTo(2L);
        assertThat(existing.getPublishedRevision()).isEqualTo(1L);
        assertThat(existing.isPublished()).isFalse();
        verify(repository).save(existing);
    }

    /**
     * The whole point of taking the tenant from the request path: an export from one organization has
     * to be importable into another unchanged.
     */
    @Test
    void anOrgKeyInTheFileIsIgnoredInFavourOfTheRequestPath() throws IOException {
        String fromAnotherTenant = VALID_FILE.replace("  - id: claims-app",
                "  - orgKey: someone-else\n    id: claims-app");

        importAppDefinitions.execute(ORG_KEY, yaml(fromAnotherTenant));

        assertThat(captureSaved().getOrgKey()).isEqualTo(ORG_KEY);
    }

    /** A blank id is as unusable as an absent one: the id is the app's route path segment. */
    @Test
    void anEntryWithoutAUsableId_isReportedAndNothingIsPersisted() throws IOException {
        ImportOutcome outcome = importAppDefinitions.execute(ORG_KEY, yaml("""
                appDefinitions:
                  - name: Nameless
                  - id: "   "
                    name: Blank
                """));

        assertThat(outcome.errors())
                .containsExactly("An app definition entry is missing 'id' and was skipped.",
                        "An app definition entry is missing 'id' and was skipped.");
        assertThat(outcome.created()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void twoEntriesSharingAnId_areReportedRatherThanSilentlyCollapsed() throws IOException {
        ImportOutcome outcome = importAppDefinitions.execute(ORG_KEY, yaml("""
                appDefinitions:
                  - id: claims-app
                    name: First
                  - id: claims-app
                    name: Second
                """));

        assertThat(outcome.errors())
                .contains("Duplicate app definition id within the import file: 'claims-app'.");
        verify(repository, never()).save(any());
    }

    @Test
    void oneStructurallyInvalidEntry_rejectsTheWholeFile() throws IOException {
        String withTitlelessRoute = VALID_FILE + """
                  - id: second-app
                    name: Second
                    routes:
                      - path: titleless
                """;

        ImportOutcome outcome = importAppDefinitions.execute(ORG_KEY, yaml(withTitlelessRoute));

        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isZero();
        // The message names the entry and the offending node, so a file with several entries says which.
        assertThat(outcome.errors()).singleElement().asString()
                .contains("'second-app'", "/routes/0/title", "needs a title");
        verify(repository, never()).save(any());
    }

    /** Otherwise a tenant whose own rules report advice could not import its own export. */
    @Test
    void nonBlockingRuleProblems_doNotFailAnOtherwiseImportableFile() throws IOException {
        AppDefinitionValidator lenient = mock(AppDefinitionValidator.class);
        when(lenient.validate(anyString(), any())).thenReturn(List.of(
                new AppValidationProblem("/", "rule.appDefinition.titlesAreTranslatable",
                        "Give every route title a Transloco id.", Severity.INFO)));
        ImportAppDefinitions withAdvice = new ImportAppDefinitions(repository, tenantDirectory,
                lenient, AppTestFixtures.permissiveGuard(), new AppMapper());

        ImportOutcome outcome = withAdvice.execute(ORG_KEY, yaml(VALID_FILE));

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.errors()).isEmpty();
    }

    @Test
    void anEmptyDocument_importsNothingAndReportsNothing() throws IOException {
        ImportOutcome outcome = importAppDefinitions.execute(ORG_KEY, yaml("{}\n"));

        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.errors()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void unknownOrganization_is404BeforeTheFileIsEvenRead() {
        ImportAppDefinitions withoutTheTenant = new ImportAppDefinitions(repository,
                AppTestFixtures.tenantDirectory(), AppTestFixtures.structuralValidator(),
                AppTestFixtures.permissiveGuard(), new AppMapper());

        assertThatThrownBy(() -> withoutTheTenant.execute(ORG_KEY, yaml(VALID_FILE)))
                .isInstanceOf(UnknownTenantException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void malformedYaml_failsAsAnIoException() {
        assertThatThrownBy(() -> importAppDefinitions.execute(ORG_KEY, yaml("appDefinitions: [")))
                .isInstanceOf(IOException.class);
    }

    @Test
    void aPrincipalWithoutDesignRights_isRejectedBeforeAnythingIsRead() {
        ImportAppDefinitions guarded = new ImportAppDefinitions(repository, tenantDirectory,
                AppTestFixtures.structuralValidator(), AppTestFixtures.denyingGuard(), new AppMapper());

        assertThatThrownBy(() -> guarded.execute(ORG_KEY, yaml(VALID_FILE)))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void aRejectedOutcomeCarriesNoCountsAndToleratesAnAbsentErrorList() {
        assertThat(ImportOutcome.rejected(List.of("boom")))
                .satisfies(outcome -> {
                    assertThat(outcome.created()).isZero();
                    assertThat(outcome.updated()).isZero();
                    assertThat(outcome.errors()).containsExactly("boom");
                });
        assertThat(new ImportOutcome(1, 2, null).errors()).isEmpty();
    }

    private AppDefinition captureSaved() {
        org.mockito.ArgumentCaptor<AppDefinition> saved =
                org.mockito.ArgumentCaptor.forClass(AppDefinition.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
