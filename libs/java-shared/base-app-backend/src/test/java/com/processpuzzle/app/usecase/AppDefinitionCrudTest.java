package com.processpuzzle.app.usecase;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.OrganizationAccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.processpuzzle.app.AppTestFixtures.APP_ID;
import static com.processpuzzle.app.AppTestFixtures.ORG_KEY;
import static com.processpuzzle.app.AppTestFixtures.ROUTE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The single-definition read, delete and dry-run-validate use cases. Each is one guard call plus one
 * repository call, so what is worth pinning down is that the guard runs first, that a miss is a 404
 * rather than an empty answer, and that validating never writes.
 */
class AppDefinitionCrudTest {

    private AppDefinitionRepository repository;
    private FindAppDefinition findAppDefinition;
    private DeleteAppDefinition deleteAppDefinition;
    private ValidateAppDefinition validateAppDefinition;

    @BeforeEach
    void setUp() {
        repository = mock(AppDefinitionRepository.class);
        findAppDefinition = new FindAppDefinition(repository, AppTestFixtures.permissiveGuard());
        deleteAppDefinition = new DeleteAppDefinition(repository, AppTestFixtures.permissiveGuard());
        validateAppDefinition = new ValidateAppDefinition(AppTestFixtures.structuralValidator(),
                AppTestFixtures.permissiveGuard());
    }

    @Test
    void findReturnsTheWholeUnfilteredAuthoringGraph() {
        AppDefinition stored = AppTestFixtures.storedDefinition();
        when(repository.findByOrgKeyAndId(ORG_KEY, APP_ID)).thenReturn(Optional.of(stored));

        AppDefinition found = findAppDefinition.execute(ORG_KEY, APP_ID);

        assertThat(found).isSameAs(stored);
        assertThat(found.getDraftGraph().routes()).extracting(route -> route.path()).containsExactly(ROUTE_PATH);
    }

    @Test
    void findingAnUnknownDefinition_is404() {
        when(repository.findByOrgKeyAndId(ORG_KEY, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findAppDefinition.execute(ORG_KEY, "nope"))
                .isInstanceOf(AppDefinitionNotFoundException.class)
                .hasMessageContaining(ORG_KEY + "/nope");
    }

    @Test
    void deleteRemovesTheDefinitionItLoaded() {
        AppDefinition stored = AppTestFixtures.storedDefinition();
        when(repository.findByOrgKeyAndId(ORG_KEY, APP_ID)).thenReturn(Optional.of(stored));

        deleteAppDefinition.execute(ORG_KEY, APP_ID);

        verify(repository).delete(stored);
    }

    @Test
    void deletingAnUnknownDefinition_is404RatherThanASilentNoOp() {
        when(repository.findByOrgKeyAndId(ORG_KEY, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteAppDefinition.execute(ORG_KEY, "nope"))
                .isInstanceOf(AppDefinitionNotFoundException.class);

        verify(repository, never()).delete(any(AppDefinition.class));
    }

    /**
     * The designer asks for live feedback, so an invalid candidate is the expected case here — it
     * comes back as a list rather than as an exception, and nothing is persisted either way.
     */
    @Test
    void validateReportsProblemsWithoutPersistingAnything() {
        AppDefinitionInput broken = AppTestFixtures.validInput(APP_ID);
        broken.getRegions().getFirst().getNavItems().getFirst().setRoutePath("route-missing");

        assertThat(validateAppDefinition.execute(ORG_KEY, broken))
                .extracting(AppValidationProblem::errorId)
                .contains("app.validation.unknown-route-reference", "app.validation.orphan-route");
        verifyNoInteractions(repository);
    }

    @Test
    void validateReportsNothingForASoundCandidate() {
        assertThat(validateAppDefinition.execute(ORG_KEY, AppTestFixtures.validInput(APP_ID))).isEmpty();
    }

    @Test
    void everyOperationConsultsTheGuardFirst() {
        AppDefinitionRepository untouched = mock(AppDefinitionRepository.class);

        assertThatThrownBy(() -> new FindAppDefinition(untouched, AppTestFixtures.denyingGuard())
                .execute(ORG_KEY, APP_ID)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> new DeleteAppDefinition(untouched, AppTestFixtures.denyingGuard())
                .execute(ORG_KEY, APP_ID)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> new ValidateAppDefinition(AppTestFixtures.structuralValidator(),
                AppTestFixtures.denyingGuard()).execute(ORG_KEY, AppTestFixtures.validInput(APP_ID)))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(untouched);
    }
}
