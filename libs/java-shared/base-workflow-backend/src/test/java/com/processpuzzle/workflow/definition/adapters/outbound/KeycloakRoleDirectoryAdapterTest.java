package com.processpuzzle.workflow.definition.adapters.outbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.workflow.definition.usecases.outbound.NoOpRoleDirectoryPort;
import com.processpuzzle.workflow.definition.usecases.outbound.RoleDirectoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakRoleDirectoryAdapterTest {

    private static final String ORG = "acme";

    private KeycloakAdminClient client;
    private KeycloakRoleDirectoryAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(KeycloakAdminClient.class);
        adapter = new KeycloakRoleDirectoryAdapter(client);
    }

    /**
     * Keycloak has no upsert, so the branch is chosen from a read. A regression that always POSTed
     * would answer 409 for every edit — tolerated, and therefore silently a no-op: descriptions would
     * simply stop following the catalog.
     */
    @Test
    void updatesAnExistingRoleThroughItsOwnPath() {
        when(client.getList("/admin/realms/acme/roles"))
                .thenReturn(List.of(Map.of("id", "reviewer-id", "name", "reviewer")));

        adapter.upsertRole(ORG, "reviewer", "Reviews submissions");

        verify(client).exchange(HttpMethod.PUT, "/admin/realms/acme/roles/reviewer",
                representation("reviewer", "Reviews submissions"), null);
        verify(client, never()).exchangeTolerating(any(), anyString(), any(), anyInt());
    }

    @Test
    void createsAMissingRoleAndToleratesTheConcurrentCreate() {
        when(client.getList("/admin/realms/acme/roles")).thenReturn(List.of());

        adapter.upsertRole(ORG, "reviewer", null);

        verify(client).exchangeTolerating(HttpMethod.POST, "/admin/realms/acme/roles",
                representation("reviewer", null), 409);
    }

    /**
     * The branch must be decided from {@code GET /roles} and never from {@code GET /roles/{name}}.
     * Keycloak answers 404 for a role that is not there, and {@code KeycloakAdminClient} raises any
     * 4xx as a failure rather than returning an empty {@code Optional} — so a single-role probe sends
     * every <em>create</em> down the failure path, and no role a tenant authors ever reaches the
     * realm. Verified against a real Keycloak; this is what stops it coming back.
     */
    @Test
    void neverProbesTheSingleRolePathWhereAMissingRoleIsAFailureRatherThanAnEmptyAnswer() {
        when(client.getList("/admin/realms/acme/roles")).thenReturn(List.of());
        when(client.exchange(eq(HttpMethod.GET), anyString(), any(), any()))
                .thenThrow(new IdentityProviderUnavailableException(
                        "Keycloak admin call failed: GET /admin/realms/acme/roles/reviewer",
                        new IllegalStateException("404 Not Found")));

        assertThatCode(() -> adapter.upsertRole(ORG, "reviewer", "Reviewer")).doesNotThrowAnyException();

        verify(client).exchangeTolerating(HttpMethod.POST, "/admin/realms/acme/roles",
                representation("reviewer", "Reviewer"), 409);
    }

    @Test
    void deletesToleratingARoleThatIsAlreadyGone() {
        adapter.deleteRole(ORG, "reviewer");

        verify(client).exchangeTolerating(
                HttpMethod.DELETE, "/admin/realms/acme/roles/reviewer", null, 404);
    }

    /**
     * A role Keycloak reports without a name would otherwise become a {@code null} in the diff set
     * and make {@code Set.contains} answer for a definition that is genuinely missing.
     */
    @Test
    void readsRealmRoleNamesIncludingTheOnesNoDefinitionProduced() {
        when(client.getList("/admin/realms/acme/roles")).thenReturn(List.of(
                Map.of("id", "1", "name", "reviewer"),
                Map.of("id", "2", "name", "org-admin"),
                Map.of("id", "3", "name", "default-roles-acme"),
                Map.of("id", "4")));

        assertThat(adapter.findRoleNames(ORG))
                .containsExactly("reviewer", "org-admin", "default-roles-acme");
    }

    /**
     * Transport failures are left as core's exception rather than re-wrapped: unlike org-admin's
     * adapter, which answers an HTTP request and needs its own module's 503 advice, nothing here is
     * answering anyone — {@code SyncRoleDirectory} catches this and logs.
     */
    @Test
    void letsTransportFailuresThroughForTheUseCaseToLog() {
        when(client.getList(anyString()))
                .thenThrow(new IdentityProviderUnavailableException("Keycloak admin call failed"));
        assertThatThrownBy(() -> adapter.upsertRole(ORG, "reviewer", null))
                .isInstanceOf(IdentityProviderUnavailableException.class);

        doThrow(new IdentityProviderUnavailableException("Keycloak admin call failed"))
                .when(client).exchangeTolerating(any(), anyString(), any(), anyInt());
        assertThatThrownBy(() -> adapter.deleteRole(ORG, "reviewer"))
                .isInstanceOf(IdentityProviderUnavailableException.class);

        assertThatThrownBy(() -> adapter.findRoleNames(ORG))
                .isInstanceOf(IdentityProviderUnavailableException.class);
    }

    /** The realm is {@code orgKey} verbatim — the platform's naming convention, applied here. */
    @Test
    void resolvesTheRealmFromTheOrganizationKey() {
        when(client.getList("/admin/realms/processpuzzle-testbed/roles")).thenReturn(List.of());

        adapter.upsertRole("processpuzzle-testbed", "reviewer", "Reviewer");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(client).exchangeTolerating(eq(HttpMethod.POST), path.capture(), any(), anyInt());
        assertThat(path.getValue()).isEqualTo("/admin/realms/processpuzzle-testbed/roles");
    }

    @Nested
    class Configuration {

        private final RoleDirectoryConfiguration configuration = new RoleDirectoryConfiguration();

        @Test
        void registersTheNoOpDirectoryWithoutAnAdminSecret() {
            when(client.isConfigured()).thenReturn(false);

            assertThat(configuration.roleDirectoryPort(client)).isInstanceOf(NoOpRoleDirectoryPort.class);
        }

        @Test
        void registersTheKeycloakDirectoryWithAnAdminSecret() {
            when(client.isConfigured()).thenReturn(true);

            assertThat(configuration.roleDirectoryPort(client))
                    .isInstanceOf(KeycloakRoleDirectoryAdapter.class);
        }
    }

    /**
     * The no-op ignores writes rather than refusing them — see its Javadoc for why it diverges from
     * org-admin's, which throws. This is the assertion that would fail if someone "fixed" it into
     * throwing, taking role authoring out of service in every Keycloak-less deployment.
     */
    @Nested
    class NoOp {

        private final RoleDirectoryPort port = new NoOpRoleDirectoryPort();

        @Test
        void ignoresWritesAndAnswersReadsEmpty() {
            assertThatCode(() -> port.upsertRole(ORG, "reviewer", "Reviewer")).doesNotThrowAnyException();
            assertThatCode(() -> port.deleteRole(ORG, "reviewer")).doesNotThrowAnyException();
            assertThat(port.findRoleNames(ORG)).isEmpty();
        }
    }

    /** Keycloak wants the name in the body of a PUT too, not only in the path. */
    private static Map<String, Object> representation(String name, String description) {
        Map<String, Object> representation = new LinkedHashMap<>();
        representation.put("name", name);
        representation.put("description", description);
        return representation;
    }
}
