package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.OrgAdminTestFixtures;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownRoleException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.ORG_KEY;
import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationRoleUseCasesTest {

    private UserDirectoryPort directory;
    private TenantRealmResolver realms;

    @BeforeEach
    void setUp() {
        directory = mock(UserDirectoryPort.class);
        realms = OrgAdminTestFixtures.resolver();
        when(directory.findUser(anyString(), anyString()))
                .thenReturn(Optional.of(OrgAdminTestFixtures.user("org-member")));
        when(directory.findRoles(anyString())).thenReturn(List.of(
                OrgAdminTestFixtures.role("org-admin"),
                OrgAdminTestFixtures.role("org-member"),
                OrgAdminTestFixtures.role("claims-auditor")));
        when(directory.findUserRoles(anyString(), anyString()))
                .thenReturn(List.of(OrgAdminTestFixtures.role("org-member")));
        when(directory.replaceRoles(anyString(), anyString(), anyList()))
                .thenAnswer(call -> ((List<String>) call.getArgument(2)).stream()
                        .map(OrgAdminTestFixtures::role).toList());
    }

    /**
     * Read live from the realm, so the two ProcessPuzzle interprets and the tenant's own arrive
     * together — distinguished by the flag rather than by the caller having to know the pair.
     */
    @Test
    void theRealmsRolesAreListedWithThePlatformOnesFlagged() {
        List<DirectoryRole> roles = new FindOrganizationRoles(realms, directory).execute(ORG_KEY);

        assertThat(roles).extracting(DirectoryRole::name)
                .containsExactly("org-admin", "org-member", "claims-auditor");
        assertThat(roles).filteredOn(DirectoryRole::platformManaged)
                .extracting(DirectoryRole::name).containsExactly("org-admin", "org-member");
    }

    @Test
    void aUsersOwnRolesAreListed() {
        assertThat(new FindOrganizationUserRoles(realms, directory).execute(ORG_KEY, USER_ID))
                .extracting(DirectoryRole::name).containsExactly("org-member");
    }

    @Test
    void readingRolesOfAnAbsentUserIs404() {
        when(directory.findUser(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new FindOrganizationUserRoles(realms, directory)
                .execute(ORG_KEY, "nope"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void aReplacementIsForwardedWhenEveryNameIsDeclaredByTheRealm() {
        List<DirectoryRole> after = new ReplaceOrganizationUserRoles(realms, directory)
                .execute(ORG_KEY, USER_ID, List.of("org-member", "claims-auditor"));

        assertThat(after).extracting(DirectoryRole::name)
                .containsExactly("org-member", "claims-auditor");
        verify(directory).replaceRoles(ORG_KEY, USER_ID, List.of("org-member", "claims-auditor"));
    }

    /**
     * The whole payload is validated before anything is written, so one typo changes nothing rather
     * than applying the rest — and the role is refused rather than created, because a minted role is
     * one nothing in the platform ever matches.
     */
    @Test
    void oneUnknownNameRejectsTheWholeReplacement() {
        assertThatThrownBy(() -> new ReplaceOrganizationUserRoles(realms, directory)
                .execute(ORG_KEY, USER_ID, List.of("claims-auditor", "claims-auditer")))
                .isInstanceOf(UnknownRoleException.class)
                .hasMessageContaining("claims-auditer");

        verify(directory, never()).replaceRoles(anyString(), anyString(), anyList());
    }

    /** Revoking everything is a legitimate request, not an empty one to be ignored. */
    @Test
    void anEmptySetRevokesEveryRole() {
        new ReplaceOrganizationUserRoles(realms, directory).execute(ORG_KEY, USER_ID, List.of());

        verify(directory).replaceRoles(ORG_KEY, USER_ID, List.of());
    }

    @Test
    void anAbsentRoleListIsTreatedAsEmptyRatherThanCrashing() {
        new ReplaceOrganizationUserRoles(realms, directory).execute(ORG_KEY, USER_ID, null);

        verify(directory).replaceRoles(ORG_KEY, USER_ID, List.of());
    }

    @Test
    void replacingRolesOfAnAbsentUserIs404AndWritesNothing() {
        when(directory.findUser(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ReplaceOrganizationUserRoles(realms, directory)
                .execute(ORG_KEY, "nope", List.of("org-member")))
                .isInstanceOf(UserNotFoundException.class);

        verify(directory, never()).replaceRoles(anyString(), anyString(), anyList());
    }

    /**
     * No use case in this module is {@code @Transactional}, and that is deliberate rather than an
     * omission: there is nothing transactional to do — Keycloak is the store, and the only database
     * read is the resolver's, which opens its own. An annotation here would suggest an atomicity
     * guarantee that does not exist.
     */
    @Test
    void noUseCaseClaimsATransactionItCannotHonour() {
        assertThat(List.of(FindOrganizationUsers.class, FindOrganizationUser.class,
                        InviteOrganizationUser.class, UpdateOrganizationUser.class,
                        DeleteOrganizationUser.class, FindOrganizationRoles.class,
                        FindOrganizationUserRoles.class, ReplaceOrganizationUserRoles.class))
                .allSatisfy(useCase -> assertThat(useCase.getAnnotation(Transactional.class)).isNull());
    }
}
