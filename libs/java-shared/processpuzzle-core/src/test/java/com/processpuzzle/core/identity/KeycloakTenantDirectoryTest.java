package com.processpuzzle.core.identity;

import com.processpuzzle.core.identity.TenantScope.OrganizationScope;
import com.processpuzzle.core.identity.TenantScope.RealmScope;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakTenantDirectoryTest {

    private static final String BASE = "https://keycloak.example";
    private static final String ORG = BASE + "/admin/realms/shared/organizations/org-1";

    @Test
    void realmPerTenantResolvesTheRealmNamedAfterTheOrgKeyWithoutAsking() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(never(), requestTo(BASE + "/realms/master/protocol/openid-connect/token"));

        KeycloakTenantDirectory tenants = directory(builder, "");

        assertThat(tenants.tenancy()).isEqualTo(KeycloakTenancy.REALM_PER_TENANT);
        assertThat(tenants.resolve("acme")).contains(new RealmScope("acme"));
        assertThat(tenants.resolve("acme").orElseThrow().adminRealmPath()).isEqualTo("/admin/realms/acme");
        assertThat(tenants.organization("acme")).isEmpty();
        server.verify();
    }

    @Test
    void unconfiguredResolvesNothing() {
        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        KeycloakTenantDirectory tenants =
                new KeycloakTenantDirectory(new KeycloakAdminClient(properties, RestClient.builder()));

        assertThat(tenants.tenancy()).isEqualTo(KeycloakTenancy.UNCONFIGURED);
        assertThat(tenants.resolve("acme")).isEmpty();
    }

    @Test
    void organizationModeResolvesByExactAliasNeverALookAlike() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakTenantDirectory tenants = directory(builder, "shared");

        expectToken(server);
        server.expect(requestTo(BASE + "/admin/realms/shared/organizations?exact=true&search=acme"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"org-1\",\"alias\":\"acme\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/admin/realms/shared/organizations?exact=true&search=acm"))
                .andRespond(withSuccess("[{\"id\":\"org-1\",\"alias\":\"acme\"}]", MediaType.APPLICATION_JSON));

        OrganizationScope acme = new OrganizationScope("shared", "org-1", "acme");
        assertThat(tenants.resolve("acme")).contains(acme);
        assertThat(acme.organizationPath()).isEqualTo("/admin/realms/shared/organizations/org-1");
        assertThat(tenants.resolve("acm")).isEmpty();
        server.verify();
    }

    @Test
    void topLevelGroupsAreNameToIdWithoutNestedGroups() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakTenantDirectory tenants = directory(builder, "shared");

        expectToken(server);
        server.expect(requestTo(ORG + "/groups?first=0&max=" + KeycloakTenantDirectory.GROUP_PAGE))
                .andRespond(withSuccess("""
                        [{"id":"g-1","name":"reviewer","path":"/reviewer"},
                         {"id":"g-2","name":"backend","path":"/reviewer/backend"},
                         {"id":"g-3","name":"approver","path":"/approver"}]""", MediaType.APPLICATION_JSON));

        assertThat(tenants.topLevelGroups(new OrganizationScope("shared", "org-1", "acme")))
                .containsExactly(Map.entry("reviewer", "g-1"), Map.entry("approver", "g-3"));
        server.verify();
    }

    @Test
    void aNonMemberIsAbsentAndAnOutageIsRaised() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakTenantDirectory tenants = directory(builder, "shared");
        OrganizationScope acme = new OrganizationScope("shared", "org-1", "acme");

        expectToken(server);
        server.expect(requestTo(ORG + "/members/alice"))
                .andRespond(withSuccess("{\"id\":\"alice\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ORG + "/members/mallory")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(ORG + "/members/bob")).andRespond(withServerError());

        assertThat(tenants.findMember(acme, "alice")).contains(Map.of("id", "alice"));
        assertThat(tenants.findMember(acme, "mallory")).isEmpty();
        assertThatThrownBy(() -> tenants.findMember(acme, "bob"))
                .isInstanceOf(IdentityProviderUnavailableException.class);
        server.verify();
    }

    private static KeycloakTenantDirectory directory(RestClient.Builder builder, String organizationRealm) {
        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setUrl(BASE);
        properties.setClientSecret("secret");
        properties.setOrganizationRealm(organizationRealm);
        return new KeycloakTenantDirectory(new KeycloakAdminClient(properties, builder));
    }

    private static void expectToken(MockRestServiceServer server) {
        server.expect(once(), requestTo(BASE + "/realms/master/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"token-1\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
    }
}
