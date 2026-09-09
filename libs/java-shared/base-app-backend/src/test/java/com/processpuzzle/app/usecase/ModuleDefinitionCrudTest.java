package com.processpuzzle.app.usecase;

import com.processpuzzle.app.usecase.port.TenantDirectory;
import org.springframework.beans.factory.ObjectProvider;
import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.model.ModuleDefinitionInput;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.exception.UnknownTenantException;
import com.processpuzzle.core.tenancy.OrganizationAccessPolicy;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.processpuzzle.app.AppTestFixtures.MODULE_KEY;
import static com.processpuzzle.app.AppTestFixtures.MODULE_ROUTE_PATH;
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
 * Module CRUD. A module is an aggregate of its own, so much of what these pin down is what does
 * <em>not</em> happen: creating one touches no app definition, deleting one leaves the mounts that
 * name it alone, and the key in the path — not the one in the body — decides which module a PUT
 * replaces.
 */
class ModuleDefinitionCrudTest {

    private ModuleDefinitionRepository repository;
    private ObjectProvider<TenantDirectory> tenantDirectory;
    private CreateModuleDefinition createModuleDefinition;
    private FindModuleDefinition findModuleDefinition;
    private FindAllModuleDefinitions findAllModuleDefinitions;
    private UpdateModuleDefinition updateModuleDefinition;
    private DeleteModuleDefinition deleteModuleDefinition;

    @BeforeEach
    void setUp() {
        repository = mock(ModuleDefinitionRepository.class);
        tenantDirectory = AppTestFixtures.tenantDirectory(ORG_KEY);
        when(repository.save(any(ModuleDefinition.class))).thenAnswer(call -> call.getArgument(0));
        createModuleDefinition = new CreateModuleDefinition(repository, tenantDirectory,
                AppTestFixtures.structuralValidator(), AppTestFixtures.permissiveGuard(), new AppMapper());
        findModuleDefinition = new FindModuleDefinition(repository, AppTestFixtures.permissiveGuard());
        findAllModuleDefinitions = new FindAllModuleDefinitions(repository, AppTestFixtures.permissiveGuard());
        updateModuleDefinition = new UpdateModuleDefinition(repository, AppTestFixtures.structuralValidator(),
                AppTestFixtures.permissiveGuard(), new AppMapper());
        deleteModuleDefinition = new DeleteModuleDefinition(repository, AppTestFixtures.permissiveGuard());
    }

    // --- create --------------------------------------------------------------------------

    @Test
    void createPersistsTheModuleWithItsRoutes() {
        ModuleDefinition created = createModuleDefinition.execute(ORG_KEY,
                AppTestFixtures.validModuleInput(MODULE_KEY));

        assertThat(created.getOrgKey()).isEqualTo(ORG_KEY);
        assertThat(created.getKey()).isEqualTo(MODULE_KEY);
        assertThat(created.getName()).isEqualTo("Claims");
        assertThat(created.getRoutes()).extracting(route -> route.path()).containsExactly(MODULE_ROUTE_PATH);
        verify(repository).save(created);
    }

    /** Absent an explicit scope the key is the scope, so a module always has one to load against. */
    @Test
    void createDefaultsTheTranslocoScopeToTheKey() {
        ModuleDefinition created = createModuleDefinition.execute(ORG_KEY,
                AppTestFixtures.validModuleInput(MODULE_KEY));

        assertThat(created.getTranslocoScope()).isEqualTo(MODULE_KEY);
    }

    /** {@code save} merges for an assigned id, so without the pre-check this would silently overwrite. */
    @Test
    void creatingAModuleThatAlreadyExists_is409RatherThanASilentOverwrite() {
        when(repository.existsByOrgKeyAndKey(ORG_KEY, MODULE_KEY)).thenReturn(true);

        assertThatThrownBy(() -> createModuleDefinition.execute(ORG_KEY,
                AppTestFixtures.validModuleInput(MODULE_KEY)))
                .isInstanceOf(ModuleDefinitionAlreadyExistsException.class)
                .hasMessageContaining(ORG_KEY + "/" + MODULE_KEY);
        verify(repository, never()).save(any());
    }

    @Test
    void creatingAModuleInAnUnknownOrganization_is404() {
        // 'nope' is absent from the directory wired in setUp.

        assertThatThrownBy(() -> createModuleDefinition.execute("nope",
                AppTestFixtures.validModuleInput(MODULE_KEY)))
                .isInstanceOf(UnknownTenantException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void creatingAStructurallyInvalidModule_is400CarryingTheProblems() {
        ModuleDefinitionInput input = AppTestFixtures.validModuleInput(MODULE_KEY);
        input.getRoutes().add(AppTestFixtures.routeDefinition(MODULE_ROUTE_PATH, "Duplicate"));

        assertThatThrownBy(() -> createModuleDefinition.execute(ORG_KEY, input))
                .isInstanceOf(ModuleDefinitionInvalidException.class)
                .satisfies(thrown -> assertThat(((ModuleDefinitionInvalidException) thrown).getProblems())
                        .extracting(AppValidationProblem::errorId)
                        .contains("app.validation.duplicate-route-path"));
        verify(repository, never()).save(any());
    }

    /**
     * A module route nothing links to is normal — the sidenav that reaches it lives in the app, which
     * this aggregate cannot see. Were the app-level orphan check applied here no module would validate.
     */
    @Test
    void aModuleRouteNoNavigationReaches_isNotReportedAsAnOrphan() {
        assertThat(AppTestFixtures.structuralValidator()
                .validateModule(ORG_KEY, AppTestFixtures.validModuleInput(MODULE_KEY))).isEmpty();
    }

    @Test
    void createRequiresDesignRights() {
        ModuleDefinitionRepository untouched = mock(ModuleDefinitionRepository.class);
        ObjectProvider<TenantDirectory> untouchedOrgs = AppTestFixtures.tenantDirectory(ORG_KEY);

        assertThatThrownBy(() -> new CreateModuleDefinition(untouched, untouchedOrgs,
                AppTestFixtures.structuralValidator(), AppTestFixtures.denyingGuard(), new AppMapper())
                .execute(ORG_KEY, AppTestFixtures.validModuleInput(MODULE_KEY)))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        verifyNoInteractions(untouched, untouchedOrgs);
    }

    // --- read ----------------------------------------------------------------------------

    @Test
    void findReturnsTheStoredModule() {
        ModuleDefinition stored = AppTestFixtures.storedModule();
        when(repository.findByOrgKeyAndKey(ORG_KEY, MODULE_KEY)).thenReturn(Optional.of(stored));

        assertThat(findModuleDefinition.execute(ORG_KEY, MODULE_KEY)).isSameAs(stored);
    }

    @Test
    void findingAnUnknownModule_is404() {
        when(repository.findByOrgKeyAndKey(ORG_KEY, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findModuleDefinition.execute(ORG_KEY, "nope"))
                .isInstanceOf(ModuleDefinitionNotFoundException.class)
                .hasMessageContaining(ORG_KEY + "/nope");
    }

    /**
     * Lazy-loading a module is a run-time read every member makes, so this one asks for membership and
     * not for design rights — a policy granting the former and refusing the latter must still serve it,
     * while the authoring list stays closed.
     */
    @Test
    void findNeedsMembershipOnlyWhereTheAuthoringListNeedsDesign() {
        ModuleDefinition stored = AppTestFixtures.storedModule();
        when(repository.findByOrgKeyAndKey(ORG_KEY, MODULE_KEY)).thenReturn(Optional.of(stored));
        OrganizationGuard memberOnly = AppTestFixtures.guardWith(new OrganizationAccessPolicy() {
            @Override
            public void requireAccess(String orgKey) {
                // a plain member: allowed
            }

            @Override
            public void requireDesign(String orgKey) {
                throw new OrganizationAccessDeniedException(orgKey);
            }
        });

        assertThat(new FindModuleDefinition(repository, memberOnly).execute(ORG_KEY, MODULE_KEY))
                .isSameAs(stored);
        assertThatThrownBy(() -> new FindAllModuleDefinitions(repository, memberOnly).execute(ORG_KEY))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void listReturnsEveryModuleOfTheOrganization() {
        ModuleDefinition stored = AppTestFixtures.storedModule();
        when(repository.findByOrgKey(ORG_KEY)).thenReturn(List.of(stored));

        assertThat(findAllModuleDefinitions.execute(ORG_KEY)).containsExactly(stored);
    }

    // --- update --------------------------------------------------------------------------

    @Test
    void updateReplacesTheContentAndCountsTheRevision() {
        ModuleDefinition stored = AppTestFixtures.storedModule();
        when(repository.findByOrgKeyAndKey(ORG_KEY, MODULE_KEY)).thenReturn(Optional.of(stored));
        ModuleDefinitionInput input = AppTestFixtures.validModuleInput(MODULE_KEY);
        input.setName("Claims, renamed");
        input.setRoutes(List.of(AppTestFixtures.routeDefinition("closed", "Closed claims")));

        ModuleDefinition updated = updateModuleDefinition.execute(ORG_KEY, MODULE_KEY, input);

        assertThat(updated.getName()).isEqualTo("Claims, renamed");
        assertThat(updated.getRoutes()).extracting(route -> route.path()).containsExactly("closed");
        assertThat(updated.getVersion()).isEqualTo(2L);
        verify(repository).save(stored);
    }

    /** The key is immutable: an app's mount references it, so a rename by PUT would unmount silently. */
    @Test
    void updateIgnoresTheKeyInTheBodyAndKeepsThePathOne() {
        ModuleDefinition stored = AppTestFixtures.storedModule();
        when(repository.findByOrgKeyAndKey(ORG_KEY, MODULE_KEY)).thenReturn(Optional.of(stored));

        assertThat(updateModuleDefinition
                .execute(ORG_KEY, MODULE_KEY, AppTestFixtures.validModuleInput("some-other-key"))
                .getKey()).isEqualTo(MODULE_KEY);
    }

    @Test
    void updatingAnUnknownModule_is404() {
        when(repository.findByOrgKeyAndKey(ORG_KEY, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateModuleDefinition.execute(ORG_KEY, "nope",
                AppTestFixtures.validModuleInput("nope")))
                .isInstanceOf(ModuleDefinitionNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updatingWithAStructurallyInvalidBody_is400AndLeavesTheStoredModuleAlone() {
        ModuleDefinition stored = AppTestFixtures.storedModule();
        when(repository.findByOrgKeyAndKey(ORG_KEY, MODULE_KEY)).thenReturn(Optional.of(stored));
        ModuleDefinitionInput input = AppTestFixtures.validModuleInput(MODULE_KEY);
        input.setRoutes(List.of(AppTestFixtures.routeDefinition(MODULE_ROUTE_PATH, null)));

        assertThatThrownBy(() -> updateModuleDefinition.execute(ORG_KEY, MODULE_KEY, input))
                .isInstanceOf(ModuleDefinitionInvalidException.class);
        verify(repository, never()).save(any());
        assertThat(stored.getVersion()).isEqualTo(1L);
    }

    // --- delete --------------------------------------------------------------------------

    /**
     * No cascade into the apps that mount this module: the mount stays and degrades to a warning, the
     * same loose coupling that lets an app mount a module before it has been authored.
     */
    @Test
    void deleteRemovesTheModuleItLoaded() {
        ModuleDefinition stored = AppTestFixtures.storedModule();
        when(repository.findByOrgKeyAndKey(ORG_KEY, MODULE_KEY)).thenReturn(Optional.of(stored));

        deleteModuleDefinition.execute(ORG_KEY, MODULE_KEY);

        verify(repository).delete(stored);
    }

    @Test
    void deletingAnUnknownModule_is404RatherThanASilentNoOp() {
        when(repository.findByOrgKeyAndKey(ORG_KEY, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteModuleDefinition.execute(ORG_KEY, "nope"))
                .isInstanceOf(ModuleDefinitionNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void everyOperationConsultsTheGuardFirst() {
        ModuleDefinitionRepository untouched = mock(ModuleDefinitionRepository.class);

        assertThatThrownBy(() -> new FindModuleDefinition(untouched, AppTestFixtures.denyingGuard())
                .execute(ORG_KEY, MODULE_KEY)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> new FindAllModuleDefinitions(untouched, AppTestFixtures.denyingGuard())
                .execute(ORG_KEY)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> new UpdateModuleDefinition(untouched, AppTestFixtures.structuralValidator(),
                AppTestFixtures.denyingGuard(), new AppMapper())
                .execute(ORG_KEY, MODULE_KEY, AppTestFixtures.validModuleInput(MODULE_KEY)))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> new DeleteModuleDefinition(untouched, AppTestFixtures.denyingGuard())
                .execute(ORG_KEY, MODULE_KEY)).isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(untouched);
    }
}
