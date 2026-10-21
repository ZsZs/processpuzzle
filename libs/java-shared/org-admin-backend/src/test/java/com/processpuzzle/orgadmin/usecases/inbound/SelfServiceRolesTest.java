package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.security.SecurityProperties;
import com.processpuzzle.orgadmin.OrgAdminTestFixtures;
import com.processpuzzle.orgadmin.usecases.inbound.exception.SelfServiceRolesDisabledException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownRoleException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.util.List;
import java.util.Optional;

import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.ORG_KEY;
import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfServiceRolesTest {

    private static final String REALM = ORG_KEY;

    private UserDirectoryPort directory;
    private SelfServiceRolesProperties properties;
    private FindMyRoles find;
    private ReplaceMyRoles replace;

    @BeforeEach
    void setUp() {
        directory = mock(UserDirectoryPort.class);
        properties = new SelfServiceRolesProperties();
        properties.setEnabled(true);
        CurrentPrincipal principal = new CurrentPrincipal(new SecurityProperties()) {
            @Override
            public Optional<String> subject() {
                return Optional.of(USER_ID);
            }
        };
        var support = new SelfServiceRoleSupport(properties, OrgAdminTestFixtures.resolver(), principal, directory);
        find = new FindMyRoles(support);
        replace = new ReplaceMyRoles(support, directory);

        when(directory.findRoles(anyString())).thenReturn(List.of(
                OrgAdminTestFixtures.role("org-admin"),
                OrgAdminTestFixtures.role("org-member"),
                OrgAdminTestFixtures.role("platform-admin"),
                OrgAdminTestFixtures.role("offline_access"),
                OrgAdminTestFixtures.role("default-roles-my-org"),
                OrgAdminTestFixtures.role("documentation-editor"),
                OrgAdminTestFixtures.role("documentation-reviewer")));
        when(directory.findUserRoles(REALM, USER_ID)).thenReturn(List.of(
                OrgAdminTestFixtures.role("org-member"), OrgAdminTestFixtures.role("org-admin")));
    }

    @Test
    void whenDisabledEveryOperationIsRefusedBeforeTouchingTheDirectory() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> find.execute(ORG_KEY)).isInstanceOf(SelfServiceRolesDisabledException.class);
        List<String> requested = List.of("documentation-editor");
        assertThatThrownBy(() -> replace.execute(ORG_KEY, requested))
                .isInstanceOf(SelfServiceRolesDisabledException.class);
        verify(directory, never()).replaceRoles(anyString(), anyString(), anyList());
    }

    @Test
    void privilegedAndBuiltInRolesAreNotSelectable() {
        MyRoles roles = find.execute(ORG_KEY);

        assertThat(roles.selectable()).extracting(DirectoryRole::name)
                .containsExactly("documentation-editor", "documentation-reviewer");
        assertThat(roles.assigned()).containsExactly("org-member", "org-admin");
    }

    @Test
    void requestingAPrivilegedRoleIsRefusedAndWritesNothing() {
        List<String> requested = List.of("documentation-editor", "platform-admin");
        assertThatThrownBy(() -> replace.execute(ORG_KEY, requested))
                .isInstanceOf(UnknownRoleException.class);
        verify(directory, never()).replaceRoles(anyString(), anyString(), anyList());
    }

    @Test
    void anUnknownRoleIsRefused() {
        List<String> requested = List.of("typo");
        assertThatThrownBy(() -> replace.execute(ORG_KEY, requested))
                .isInstanceOf(UnknownRoleException.class);
    }

    @Test
    void privilegedRolesTheCallerHoldsArePreservedNotRevoked() {
        replace.execute(ORG_KEY, List.of("documentation-editor"));

        verify(directory).replaceRoles(REALM, USER_ID,
                List.of("org-member", "org-admin", "documentation-editor"));
    }

    @Test
    void deselectingEverythingRevokesOnlyTheSelectableRoles() {
        when(directory.findUserRoles(REALM, USER_ID)).thenReturn(List.of(
                OrgAdminTestFixtures.role("org-member"), OrgAdminTestFixtures.role("documentation-editor")));

        replace.execute(ORG_KEY, List.of());

        verify(directory).replaceRoles(REALM, USER_ID, List.of("org-member"));
    }
}
