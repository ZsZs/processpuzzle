package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.OrgAdminTestFixtures;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindRoleCatalogTest {

    @Test
    void offersTheTenantsOwnRolesAndNothingAdministrative() {
        UserDirectoryPort directory = mock(UserDirectoryPort.class);
        when(directory.findRoles(anyString())).thenReturn(List.of(
                OrgAdminTestFixtures.role("org-admin"),
                OrgAdminTestFixtures.role("org-member"),
                OrgAdminTestFixtures.role("platform-admin"),
                OrgAdminTestFixtures.role("offline_access"),
                OrgAdminTestFixtures.role("default-roles-demo"),
                OrgAdminTestFixtures.role("documentation-editor")));

        List<DirectoryRole> roles = new FindRoleCatalog(OrgAdminTestFixtures.resolver(), directory).execute(ORG_KEY);

        assertThat(roles).extracting(DirectoryRole::name).containsExactly("documentation-editor");
    }
}
