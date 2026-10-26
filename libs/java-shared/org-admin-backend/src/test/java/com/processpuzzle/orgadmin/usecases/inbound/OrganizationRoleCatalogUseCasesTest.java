package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.RoleReference;
import com.processpuzzle.orgadmin.OrgAdminTestFixtures;
import com.processpuzzle.orgadmin.usecases.inbound.exception.InvalidRoleNameException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleInUseException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleNotFoundException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RoleNotManagedException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.RoleCatalogPort;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The administrator's side of the role catalog: who may be told a role is theirs to change, what a new
 * role may be called, and that a role still in use is not deleted.
 */
class OrganizationRoleCatalogUseCasesTest {

    private static final RoleReference DOCUMENT = new RoleReference("document", "reader-role", "doc-1", "Handbook");
    private static final RoleReference DEFINITION = new RoleReference("workflow", RoleReference.ROLE_DEFINITION, "reviewer", "Reviewer");

    private UserDirectoryPort directory;
    private RoleCatalogPort catalog;
    private RoleReferences references;
    private TenantRealmResolver realms;
    private CreateOrganizationRole create;
    private UpdateOrganizationRole update;
    private DeleteOrganizationRole delete;

    @BeforeEach
    void setUp() {
        directory = mock(UserDirectoryPort.class);
        catalog = mock(RoleCatalogPort.class);
        realms = OrgAdminTestFixtures.resolver();
        references = OrgAdminTestFixtures.referencesFrom(
                org -> Map.of("reviewer", List.of(DEFINITION), "handbook-reader", List.of(DOCUMENT)));
        when(directory.findRoles(anyString())).thenReturn(List.of(
                new DirectoryRole("org-admin", "Administrators", true),
                new DirectoryRole("org-member", "Members", true),
                new DirectoryRole("offline_access", null, false),
                new DirectoryRole("reviewer", "Reviews", false),
                new DirectoryRole("handbook-reader", null, false),
                new DirectoryRole("accounting", "Books", false)));
        create = new CreateOrganizationRole(realms, directory, catalog);
        update = new UpdateOrganizationRole(realms, new ManagedRoles(directory, references), catalog);
        delete = new DeleteOrganizationRole(realms, new ManagedRoles(directory, references), catalog);
    }

    @Test
    void everyRoleIsClassifiedByWhoManagesIt() {
        List<CatalogRole> roles = new FindOrganizationRoles(realms, directory, references).execute(ORG_KEY);

        assertThat(roles).extracting(CatalogRole::name, CatalogRole::origin).containsExactly(
                org.assertj.core.groups.Tuple.tuple("org-admin", RoleOrigin.PLATFORM),
                org.assertj.core.groups.Tuple.tuple("org-member", RoleOrigin.PLATFORM),
                // The identity provider's own, although the directory does not flag it as platform-managed.
                org.assertj.core.groups.Tuple.tuple("offline_access", RoleOrigin.PLATFORM),
                org.assertj.core.groups.Tuple.tuple("reviewer", RoleOrigin.WORKFLOW),
                // Named by a document only: still the administrator's, who is told so before deleting it.
                org.assertj.core.groups.Tuple.tuple("handbook-reader", RoleOrigin.ADMIN),
                org.assertj.core.groups.Tuple.tuple("accounting", RoleOrigin.ADMIN));
    }

    @Test
    void aValidNewRoleIsCreatedAndAnswered() {
        CatalogRole created = create.execute(ORG_KEY, "claims-auditor", "  Audits claims  ");

        assertThat(created).isEqualTo(new CatalogRole("claims-auditor", "Audits claims", false, RoleOrigin.ADMIN));
        verify(catalog).createRole(ORG_KEY, "claims-auditor", "Audits claims");
    }

    @Test
    void aBlankDescriptionIsStoredAsNone() {
        create.execute(ORG_KEY, "claims-auditor", "   ");

        verify(catalog).createRole(ORG_KEY, "claims-auditor", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Claims Auditor", "", "-leading", "has.dot", "UPPER"})
    void aMalformedNameIsRefusedBeforeAnythingIsWritten(String name) {
        assertThatThrownBy(() -> create.execute(ORG_KEY, name, null)).isInstanceOf(InvalidRoleNameException.class);

        verifyNoInteractions(catalog);
    }

    @ParameterizedTest
    @ValueSource(strings = {"org-admin", "org-member", "platform-admin", "offline_access", "default-roles-acme"})
    void aNameThePlatformOrTheProviderOwnsIsRefused(String name) {
        assertThatThrownBy(() -> create.execute(ORG_KEY, name, null))
                .isInstanceOf(InvalidRoleNameException.class)
                .hasMessageContaining("belongs to ProcessPuzzle");

        verifyNoInteractions(catalog);
    }

    @Test
    void aRoleTheOrganizationAlreadyDeclaresIsRefusedRatherThanOverwritten() {
        assertThatThrownBy(() -> create.execute(ORG_KEY, "accounting", "Other"))
                .isInstanceOf(RoleAlreadyExistsException.class);

        verify(catalog, never()).createRole(anyString(), anyString(), anyString());
    }

    @Test
    void aMemberWhoMayNotAdministerCannotDefineARole() {
        TenantRealmResolver membersOnly = OrgAdminTestFixtures.resolverFor(true, OrgAdminTestFixtures.membersOnlyGuard());

        CreateOrganizationRole create = new CreateOrganizationRole(membersOnly, directory, catalog);

        assertThatThrownBy(() -> create.execute(ORG_KEY, "claims-auditor", null))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(catalog);
    }

    @Test
    void anAdministratorRolesDescriptionCanBeChanged() {
        CatalogRole updated = update.execute(ORG_KEY, "accounting", "Bookkeeping");

        assertThat(updated.description()).isEqualTo("Bookkeeping");
        assertThat(updated.origin()).isEqualTo(RoleOrigin.ADMIN);
        verify(catalog).updateDescription(ORG_KEY, "accounting", "Bookkeeping");
    }

    @ParameterizedTest
    @ValueSource(strings = {"org-admin", "offline_access", "reviewer"})
    void aRoleThePlatformOrTheDesignerOwnsCannotBeChangedHere(String name) {
        assertThatThrownBy(() -> update.execute(ORG_KEY, name, "x")).isInstanceOf(RoleNotManagedException.class);

        verify(catalog, never()).updateDescription(anyString(), anyString(), anyString());
    }

    @Test
    void changingARoleTheOrganizationDoesNotDeclareIs404() {
        assertThatThrownBy(() -> update.execute(ORG_KEY, "ghost", "x")).isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void aRoleNobodyHoldsOrNamesIsDeleted() {
        delete.execute(ORG_KEY, "accounting");

        verify(catalog).deleteRole(ORG_KEY, "accounting");
    }

    @Test
    void aRoleAUserStillHoldsIsNotDeleted() {
        when(catalog.hasHolders(ORG_KEY, "accounting")).thenReturn(true);

        assertThatThrownBy(() -> delete.execute(ORG_KEY, "accounting"))
                .isInstanceOfSatisfying(RoleInUseException.class, ex -> {
                    assertThat(ex.isHeld()).isTrue();
                    assertThat(ex.getReferences()).isEmpty();
                });

        verify(catalog, never()).deleteRole(anyString(), anyString());
    }

    @Test
    void aRoleADocumentStillNamesIsNotDeletedAndTheDocumentIsReported() {
        assertThatThrownBy(() -> delete.execute(ORG_KEY, "handbook-reader"))
                .isInstanceOfSatisfying(RoleInUseException.class, ex -> {
                    assertThat(ex.isHeld()).isFalse();
                    assertThat(ex.getReferences()).containsExactly(DOCUMENT);
                });

        verify(catalog, never()).deleteRole(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"org-member", "reviewer"})
    void aPlatformOrDesignerRoleIsNeverDeletedHere(String name) {
        assertThatThrownBy(() -> delete.execute(ORG_KEY, name)).isInstanceOf(RoleNotManagedException.class);

        verify(catalog, never()).deleteRole(anyString(), anyString());
    }

    @Test
    void deletingARoleTheOrganizationDoesNotDeclareIs404() {
        assertThatThrownBy(() -> delete.execute(ORG_KEY, "ghost")).isInstanceOf(RoleNotFoundException.class);
    }
}
