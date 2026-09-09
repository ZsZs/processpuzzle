package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.usecases.outbound.RoleDirectoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SyncRoleDirectoryTest {

    private static final String ORG = "acme";

    private RoleDefinitionRepository repository;
    private RoleDirectoryPort directory;
    private SyncRoleDirectory sync;

    @BeforeEach
    void setUp() {
        repository = mock(RoleDefinitionRepository.class);
        directory = mock(RoleDirectoryPort.class);
        sync = new SyncRoleDirectory(repository, directory);
    }

    @Test
    void projectsARoleUnderItsIdWithTheDescriptionTheAuthorGave() {
        sync.sync(ORG, "reviewer", "Reviewer", "Reviews submissions");

        verify(directory).upsertRole(ORG, "reviewer", "Reviews submissions");
    }

    /**
     * A realm role with no description at all is hard to place in the Keycloak console, so the
     * display name stands in — and when there is neither, the field is left unset rather than blank.
     */
    @Test
    void fallsBackToTheDisplayNameAndThenToNoDescription() {
        sync.sync(ORG, "reviewer", "Reviewer", "  ");
        verify(directory).upsertRole(ORG, "reviewer", "Reviewer");

        sync.sync(ORG, "auditor", null, null);
        verify(directory).upsertRole(ORG, "auditor", null);
    }

    /**
     * The reserved names are the platform's own ({@code org-admin}, {@code org-member}) and
     * Keycloak's. A definition projecting onto one of them would let a tenant grant itself platform
     * authority by authoring a row, and deleting the definition again would revoke a role the realm
     * needs — which is why {@code remove} refuses them too, not only {@code sync}.
     */
    @Test
    void refusesToTouchRealmRolesThePlatformOrKeycloakOwns() {
        for (String reserved : List.of("org-admin", "org-member", "offline_access", "uma_authorization",
                "default-roles-acme")) {
            sync.sync(ORG, reserved, "Nope", "Nope");
            sync.remove(ORG, reserved);
        }

        verifyNoInteractions(directory);
    }

    /**
     * Both entry points run after the writing transaction committed and the request was answered, so
     * there is nobody left to report a failure to. Letting it escape would only fill the log with
     * stack traces out of Spring's event infrastructure.
     */
    @Test
    void swallowsAnUnavailableIdentityProvider() {
        doThrow(unavailable()).when(directory).upsertRole(anyString(), anyString(), any());
        doThrow(unavailable()).when(directory).deleteRole(anyString(), anyString());

        assertThatCode(() -> sync.sync(ORG, "reviewer", "Reviewer", null)).doesNotThrowAnyException();
        assertThatCode(() -> sync.remove(ORG, "reviewer")).doesNotThrowAnyException();
    }

    @Test
    void removesTheRealmRoleOfADeletedDefinition() {
        sync.remove(ORG, "reviewer");

        verify(directory).deleteRole(ORG, "reviewer");
    }

    /**
     * The reconcile is the whole reason a failed projection is survivable, and its two halves are
     * both asserted here: it creates what the realm is missing, and it removes <b>nothing</b>. A
     * realm's roles are one namespace shared with {@code org-admin}, {@code default-roles-<realm>}
     * and whatever an administrator made by hand, so a prune would break the realm on its first run.
     */
    @Test
    void reconcileCreatesWhatIsMissingPerOrganizationAndPrunesNothing() {
        when(repository.findAll()).thenReturn(List.of(
                role(ORG, "reviewer", "Reviewer", "Reviews submissions"),
                role(ORG, "approver", "Approver", null),
                role("other-org", "clerk", "Clerk", null)));
        when(directory.findRoleNames(ORG)).thenReturn(List.of(
                "reviewer", "org-admin", "default-roles-acme", "made-by-hand"));
        when(directory.findRoleNames("other-org")).thenReturn(List.of());

        sync.reconcile();

        verify(directory).upsertRole(ORG, "approver", "Approver");
        verify(directory).upsertRole("other-org", "clerk", "Clerk");
        verify(directory, never()).upsertRole(eq(ORG), eq("reviewer"), any());
        verify(directory, never()).deleteRole(anyString(), anyString());
    }

    /** One unreachable realm must not cost the others their reconcile. */
    @Test
    void reconcileKeepsGoingWhenOneRealmCannotBeRead() {
        when(repository.findAll()).thenReturn(List.of(
                role(ORG, "reviewer", "Reviewer", null),
                role("other-org", "clerk", "Clerk", null)));
        when(directory.findRoleNames(ORG)).thenThrow(unavailable());
        when(directory.findRoleNames("other-org")).thenReturn(List.of());

        assertThatCode(() -> sync.reconcile()).doesNotThrowAnyException();

        verify(directory).upsertRole("other-org", "clerk", "Clerk");
        verify(directory, never()).upsertRole(eq(ORG), anyString(), any());
    }

    /**
     * Rows that predate {@code CreateRoleDefinitionUseCase}'s guard, or arrived through the import
     * path, still must not be projected — the reconcile is the one place they would otherwise be
     * retried on every restart.
     */
    @Test
    void reconcileSkipsReservedIdsAndRowsWithNoKey() {
        when(repository.findAll()).thenReturn(List.of(
                role(ORG, "org-admin", "Hijack", null),
                role(ORG, null, "No id", null),
                role(null, "orphan", "No org", null)));
        when(directory.findRoleNames(ORG)).thenReturn(List.of());

        sync.reconcile();

        verify(directory, never()).upsertRole(anyString(), anyString(), any());
    }

    @Test
    void reservedNamesAreTheDocumentedSetPlusTheDefaultRolesPrefix() {
        assertThat(SyncRoleDirectory.RESERVED_ROLE_IDS)
                .containsExactlyInAnyOrder("org-admin", "org-member", "offline_access", "uma_authorization");
        assertThat(SyncRoleDirectory.isReserved("default-roles-anything")).isTrue();
        assertThat(SyncRoleDirectory.isReserved("reviewer")).isFalse();
        assertThat(SyncRoleDirectory.isReserved(null)).isFalse();
    }

    private static RoleDefinition role(String orgKey, String id, String name, String description) {
        return RoleDefinition.builder().orgKey(orgKey).id(id).name(name).description(description).build();
    }

    private static IdentityProviderUnavailableException unavailable() {
        return new IdentityProviderUnavailableException("Keycloak admin call failed");
    }
}
