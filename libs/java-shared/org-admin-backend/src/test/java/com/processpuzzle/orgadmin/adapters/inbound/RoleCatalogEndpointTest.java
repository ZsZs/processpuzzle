package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.orgadmin.OrgAdminTestFixtures;
import com.processpuzzle.orgadmin.usecases.inbound.FindRoleCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleCatalogEndpointTest {

    private final FindRoleCatalog findRoleCatalog = mock(FindRoleCatalog.class);
    private final RoleCatalogEndpoint endpoint = new RoleCatalogEndpoint(findRoleCatalog, new OrgAdminMapper());

    @Test
    void answersTheCatalogAsRoleModels() {
        when(findRoleCatalog.execute(ORG_KEY)).thenReturn(List.of(OrgAdminTestFixtures.role("documentation-editor")));

        var response = endpoint.listRoleCatalog(ORG_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting("name").containsExactly("documentation-editor");
    }
}
