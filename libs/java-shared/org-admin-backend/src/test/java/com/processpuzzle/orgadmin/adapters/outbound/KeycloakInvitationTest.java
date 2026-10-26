package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.identity.TenantScope;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.InvitationRecoveryRequiredException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort.NewUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KeycloakInvitationTest {

    private static final String USERS = "/admin/realms/shared/users";
    private static final String USER_ID = "created-id";
    private static final NewUser NEW_USER = new NewUser("ada", "ada@example.com", "Ada", null);
    private final KeycloakAdminClient client = mock(KeycloakAdminClient.class);
    private final KeycloakInvitation invitation = new KeycloakInvitation(client);
    @SuppressWarnings("unchecked")
    private final Consumer<String> provision = mock(Consumer.class);
    @SuppressWarnings("unchecked")
    private final Function<String, DirectoryUser> read = mock(Function.class);

    @BeforeEach
    void createdAccount() {
        when(client.createAndReturnId(eq(USERS), any())).thenReturn(Optional.of(USER_ID));
        when(read.apply(USER_ID)).thenReturn(user(false), user(true));
    }

    @Test
    void provisionsAndVerifiesBeforeEnablingAndReadingTheCompletedAccount() {
        assertThat(invite()).isEqualTo(user(true));

        var order = inOrder(client, provision, read);
        order.verify(client).createAndReturnId(eq(USERS), any());
        order.verify(provision).accept(USER_ID);
        order.verify(read).apply(USER_ID);
        order.verify(client).exchange(HttpMethod.PUT, USERS + "/" + USER_ID, Map.of("enabled", true), null);
        order.verify(read).apply(USER_ID);
        order.verify(client).exchange(HttpMethod.PUT, USERS + "/" + USER_ID + "/execute-actions-email",
                List.of("UPDATE_PASSWORD"), null);
        verify(client, never()).exchangeTolerating(eq(HttpMethod.DELETE), any(), any(), eq(404));
    }

    /** A mail server that is down is a delivery problem, not a reason to undo a complete account. */
    @Test
    void aFailedMailKeepsTheInvitedAccount() {
        doThrow(unavailable()).when(client).exchange(
                HttpMethod.PUT, USERS + "/" + USER_ID + "/execute-actions-email", List.of("UPDATE_PASSWORD"), null);

        assertThat(invite()).isEqualTo(user(true));
        verify(client, never()).exchangeTolerating(eq(HttpMethod.DELETE), any(), any(), eq(404));
    }

    @Test
    void noMailIsSentWhenTheInvitationFails() {
        doThrow(new DirectoryUnavailableException("role grant failed")).when(provision).accept(USER_ID);

        assertThatThrownBy(this::invite).isInstanceOf(DirectoryUnavailableException.class);
        verify(client, never()).exchange(eq(HttpMethod.PUT), eq(USERS + "/" + USER_ID + "/execute-actions-email"),
                any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"provision", "verification", "enable", "readback", "not-enabled",
            "missing-roles", "final-missing-roles"})
    void compensatesEveryFailureAfterConfirmedCreation(String boundary) {
        DirectoryUnavailableException failure = new DirectoryUnavailableException("provider read failed");
        switch (boundary) {
            case "provision" -> doThrow(failure).when(provision).accept(USER_ID);
            case "verification" -> when(read.apply(USER_ID)).thenThrow(failure);
            case "enable" -> doThrow(unavailable()).when(client).exchange(
                    HttpMethod.PUT, USERS + "/" + USER_ID, Map.of("enabled", true), null);
            case "readback" -> when(read.apply(USER_ID)).thenReturn(user(false)).thenThrow(failure);
            case "not-enabled" -> when(read.apply(USER_ID)).thenReturn(user(false));
            case "missing-roles" -> when(read.apply(USER_ID)).thenReturn(new DirectoryUser(
                    USER_ID, "ada", "ada@example.com", "Ada", null, false, false, null, List.of()));
            case "final-missing-roles" -> when(read.apply(USER_ID)).thenReturn(user(false),
                    new DirectoryUser(USER_ID, "ada", "ada@example.com", "Ada", null,
                            true, false, null, List.of()));
            default -> throw new AssertionError(boundary);
        }

        assertThatThrownBy(this::invite).isInstanceOf(DirectoryUnavailableException.class)
                .hasMessageContaining("safe to retry");
        verify(client).exchangeTolerating(HttpMethod.DELETE, USERS + "/" + USER_ID, null, 404);
    }

    @Test
    void unconfirmedCompensationPreservesBothFailuresAndIdentifiesTheCreatedAccount() {
        DirectoryUnavailableException failure = new DirectoryUnavailableException("role grant failed");
        IdentityProviderUnavailableException cleanup = unavailable();
        doThrow(failure).when(provision).accept(USER_ID);
        doThrow(cleanup).when(client).exchangeTolerating(HttpMethod.DELETE, USERS + "/" + USER_ID, null, 404);

        assertThatThrownBy(this::invite).isInstanceOfSatisfying(
                InvitationRecoveryRequiredException.class, ex -> {
                    assertThat(ex.getUserId()).isEqualTo(USER_ID);
                    assertThat(UUID.fromString(ex.getOperationId())).isNotNull();
                    assertThat(ex.getCause()).isSameAs(failure);
                    assertThat(ex.getCause().getSuppressed()).containsExactly(cleanup);
                    assertThat(ex).hasMessageContaining("before retrying");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-id", "blank-id", "lost-response"})
    void anUnknownCreateNeverAdoptsOrDeletesAnAccount(String boundary) {
        switch (boundary) {
            case "no-id" -> when(client.createAndReturnId(eq(USERS), any())).thenReturn(Optional.empty());
            case "blank-id" -> when(client.createAndReturnId(eq(USERS), any())).thenReturn(Optional.of(""));
            case "lost-response" -> when(client.createAndReturnId(eq(USERS), any())).thenThrow(unavailable());
            default -> throw new AssertionError(boundary);
        }

        assertThatThrownBy(this::invite).isInstanceOfSatisfying(
                InvitationRecoveryRequiredException.class, ex -> {
                    assertThat(ex.getUserId()).isNull();
                    assertThat(UUID.fromString(ex.getOperationId())).isNotNull();
                });
        verifyNoInteractions(provision, read);
        verify(client, never()).getList(any());
        verify(client, never()).exchangeTolerating(eq(HttpMethod.DELETE), any(), any(), eq(404));
    }

    @Test
    void duplicatesAreRejectedWithoutProvisioningOrCleanup() {
        when(client.createAndReturnId(eq(USERS), any())).thenThrow(
                new IdentityProviderUnavailableException("create failed",
                        HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null)));

        assertThatThrownBy(this::invite).isInstanceOf(UserAlreadyExistsException.class);
        verifyNoInteractions(provision, read);
        verify(client, never()).exchangeTolerating(eq(HttpMethod.DELETE), any(), any(), eq(404));
    }

    @Test
    void aConfirmedRollbackAllowsAFreshInvitation() {
        when(client.createAndReturnId(eq(USERS), any()))
                .thenReturn(Optional.of(USER_ID), Optional.of("retry-id"));
        doThrow(new DirectoryUnavailableException("read unavailable")).when(provision).accept(USER_ID);
        DirectoryUser retried = new DirectoryUser("retry-id", "ada", "ada@example.com", "Ada", null,
                true, false, null, List.of("org-member"));
        when(read.apply("retry-id")).thenReturn(retried);
        assertThatThrownBy(this::invite).isInstanceOf(DirectoryUnavailableException.class);

        assertThat(invite()).isEqualTo(retried);
        verify(client).exchangeTolerating(HttpMethod.DELETE, USERS + "/" + USER_ID, null, 404);
        verify(provision).accept("retry-id");
        verify(client, never()).exchangeTolerating(HttpMethod.DELETE, USERS + "/retry-id", null, 404);
    }

    private DirectoryUser invite() {
        return invitation.invite(new TenantScope.RealmScope("shared"), "acme", NEW_USER, List.of("org-member"), provision, read);
    }

    private static DirectoryUser user(boolean enabled) {
        return new DirectoryUser(USER_ID, "ada", "ada@example.com", "Ada", null,
                enabled, false, null, List.of("org-member", "default-role"));
    }

    private static IdentityProviderUnavailableException unavailable() {
        return new IdentityProviderUnavailableException("Keycloak unavailable", new IllegalStateException("down"));
    }
}
