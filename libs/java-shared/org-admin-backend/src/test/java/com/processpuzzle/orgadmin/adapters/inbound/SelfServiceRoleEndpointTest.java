package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.orgadmin.model.RoleAssignment;
import com.processpuzzle.orgadmin.usecases.inbound.FindMyRoles;
import com.processpuzzle.orgadmin.usecases.inbound.MyRoles;
import com.processpuzzle.orgadmin.usecases.inbound.ReplaceMyRoles;
import com.processpuzzle.orgadmin.usecases.inbound.exception.SelfServiceRolesDisabledException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownRoleException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.ORG_KEY;
import static com.processpuzzle.orgadmin.OrgAdminTestFixtures.role;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SelfServiceRoleEndpointTest {

    private final FindMyRoles find = mock(FindMyRoles.class);
    private final ReplaceMyRoles replace = mock(ReplaceMyRoles.class);
    private final SelfServiceRoleEndpoint endpoint =
            new SelfServiceRoleEndpoint(find, replace, new OrgAdminMapper());

    @Test
    void readingAnswersAssignedAndSelectableRoles() {
        when(find.execute(ORG_KEY)).thenReturn(
                new MyRoles(List.of("org-member"), List.of(role("documentation-editor"))));

        var response = endpoint.getMyRoles(ORG_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAssigned()).containsExactly("org-member");
        assertThat(response.getBody().getSelectable()).extracting("name").containsExactly("documentation-editor");
    }

    @Test
    void replacingForwardsTheRequestedNames() {
        when(replace.execute(ORG_KEY, List.of("documentation-editor")))
                .thenReturn(new MyRoles(List.of("documentation-editor"), List.of()));

        var response = endpoint.replaceMyRoles(ORG_KEY, new RoleAssignment(List.of("documentation-editor")));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAssigned()).containsExactly("documentation-editor");
        verify(replace).execute(ORG_KEY, List.of("documentation-editor"));
    }

    @Test
    void anUnselectableRoleAnswers400() throws Exception {
        when(replace.execute(ORG_KEY, List.of("org-admin"))).thenThrow(new UnknownRoleException(ORG_KEY, "org-admin"));

        mvc().perform(put("/organizations/" + ORG_KEY + "/me/roles")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"org-admin\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorId").value("role.unknown"));
    }

    @Test
    void aDisabledFeatureAnswers404() throws Exception {
        when(find.execute(ORG_KEY)).thenThrow(new SelfServiceRolesDisabledException("off"));

        mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/organizations/" + ORG_KEY + "/me/roles"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorId").value("self-service-roles.disabled"));
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(endpoint)
                .setControllerAdvice(new OrgAdminApiExceptionHandler()).build();
    }
}
