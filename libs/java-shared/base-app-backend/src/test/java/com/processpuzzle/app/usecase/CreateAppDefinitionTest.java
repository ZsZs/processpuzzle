package com.processpuzzle.app.usecase;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.usecase.exception.AppDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.rule.domain.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static com.processpuzzle.app.AppTestFixtures.APP_ID;
import static com.processpuzzle.app.AppTestFixtures.ORG_KEY;
import static com.processpuzzle.app.AppTestFixtures.ROUTE_PATH;
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
 * The order of the checks is what this fixes in place: the tenant has to exist, the id has to be
 * free, and the definition has to be structurally sound — each before anything is written. The
 * existence check in particular cannot be dropped in favour of the database, because
 * {@code JpaRepository.save} merges rather than persists an assigned id and would turn a duplicate
 * create into a silent overwrite.
 */
class CreateAppDefinitionTest {

    private AppDefinitionRepository repository;
    private OrganizationRepository organizationRepository;
    private CreateAppDefinition createAppDefinition;

    @BeforeEach
    void setUp() {
        repository = mock(AppDefinitionRepository.class);
        when(repository.save(any(AppDefinition.class))).thenAnswer(call -> call.getArgument(0));
        when(repository.existsByOrgKeyAndId(anyString(), anyString())).thenReturn(false);

        organizationRepository = mock(OrganizationRepository.class);
        when(organizationRepository.existsById(anyString())).thenReturn(true);

        createAppDefinition = new CreateAppDefinition(repository, organizationRepository,
                AppTestFixtures.structuralValidator(), AppTestFixtures.permissiveGuard(), new AppMapper());
    }

    @Test
    void createsTheDefinitionAsRevisionOneAndUnpublished() {
        AppDefinitionInput input = AppTestFixtures.validInput(APP_ID);
        input.setTranslocoId("claims.app.name");
        input.setDescription("Handles claims.");

        AppDefinition created = createAppDefinition.execute(ORG_KEY, input);

        assertThat(created.getOrgKey()).isEqualTo(ORG_KEY);
        assertThat(created.getId()).isEqualTo(APP_ID);
        assertThat(created.getName()).isEqualTo("Claims Management");
        assertThat(created.getTranslocoId()).isEqualTo("claims.app.name");
        assertThat(created.getDescription()).isEqualTo("Handles claims.");
        assertThat(created.getRevision()).isEqualTo(1L);
        assertThat(created.isPublished()).isFalse();
        assertThat(created.hasPublishedRevision()).isFalse();
        verify(repository).save(created);
    }

    @Test
    void theInputGraphIsMappedIntoTheDraft() {
        AppDefinition created = createAppDefinition.execute(ORG_KEY, AppTestFixtures.validInput(APP_ID));

        assertThat(created.getDraftGraph().routes()).singleElement()
                .satisfies(route -> assertThat(route.path()).isEqualTo(ROUTE_PATH));
        assertThat(created.getDraftGraph().regions()).singleElement()
                .satisfies(region -> assertThat(region.navItems()).hasSize(1));
        assertThat(created.getPublishedGraph()).isNull();
    }

    @Test
    void unknownOrganization_is404AndNothingIsWritten() {
        when(organizationRepository.existsById(ORG_KEY)).thenReturn(false);

        assertThatThrownBy(() -> createAppDefinition.execute(ORG_KEY, AppTestFixtures.validInput(APP_ID)))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining(ORG_KEY);

        verifyNoInteractions(repository);
    }

    @Test
    void anIdAlreadyUsedInThisOrganization_is409RatherThanASilentOverwrite() {
        when(repository.existsByOrgKeyAndId(ORG_KEY, APP_ID)).thenReturn(true);

        assertThatThrownBy(() -> createAppDefinition.execute(ORG_KEY, AppTestFixtures.validInput(APP_ID)))
                .isInstanceOf(AppDefinitionAlreadyExistsException.class)
                .hasMessageContaining(ORG_KEY + "/" + APP_ID);

        verify(repository, never()).save(any());
    }

    @Test
    void aStructurallyInvalidDefinition_is400CarryingTheProblems() {
        AppDefinitionInput input = AppTestFixtures.validInput(APP_ID);
        input.getRoutes().add(AppTestFixtures.routeDefinition(AppTestFixtures.ROUTE_PATH, "Duplicate"));

        assertThatThrownBy(() -> createAppDefinition.execute(ORG_KEY, input))
                .isInstanceOf(AppDefinitionInvalidException.class)
                .satisfies(thrown -> assertThat(((AppDefinitionInvalidException) thrown).getProblems())
                        .extracting(AppValidationProblem::errorId)
                        .contains("app.validation.duplicate-route-path"));

        verify(repository, never()).save(any());
    }

    /**
     * A tenant's own rules also report warnings and advice. Rejecting on those would mean a draft
     * could not be saved on its way to conforming.
     */
    @Test
    void aDefinitionCarryingOnlyNonBlockingProblems_isStillCreated() {
        AppDefinitionValidator lenient = mock(AppDefinitionValidator.class);
        when(lenient.validate(anyString(), any())).thenReturn(List.of(
                new AppValidationProblem("/", "rule.appDefinition.hasNavigation",
                        "This app declares no sidenav navigation.", Severity.WARNING)));
        CreateAppDefinition withWarnings = new CreateAppDefinition(repository, organizationRepository,
                lenient, AppTestFixtures.permissiveGuard(), new AppMapper());

        assertThat(withWarnings.execute(ORG_KEY, AppTestFixtures.validInput(APP_ID))).isNotNull();
        verify(repository).save(any(AppDefinition.class));
    }

    @Test
    void aPrincipalWithoutDesignRights_isRejectedBeforeAnythingIsRead() {
        CreateAppDefinition guarded = new CreateAppDefinition(repository, organizationRepository,
                AppTestFixtures.structuralValidator(), AppTestFixtures.denyingGuard(), new AppMapper());

        assertThatThrownBy(() -> guarded.execute(ORG_KEY, AppTestFixtures.validInput(APP_ID)))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(repository, organizationRepository);
    }

    @Nested
    @DisplayName("the invalid-definition exception")
    class InvalidException {

        @Test
        void reportsHowManyProblemsRejectedTheWrite() {
            AppDefinitionInvalidException exception = new AppDefinitionInvalidException(ORG_KEY, APP_ID,
                    List.of(new AppValidationProblem("/id", "app.validation.missing-id", "Needs an id.")));

            assertThat(exception).hasMessageContaining("1 problem(s)");
            assertThat(exception.getProblems()).hasSize(1);
        }

        /** Thrown from a path that found no problems at all — the list must still be usable. */
        @Test
        void toleratesNoProblemList() {
            AppDefinitionInvalidException exception =
                    new AppDefinitionInvalidException(ORG_KEY, APP_ID, null);

            assertThat(exception).hasMessageContaining("0 problem(s)");
            assertThat(exception.getProblems()).isEmpty();
        }
    }
}
