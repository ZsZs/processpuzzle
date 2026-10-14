package com.processpuzzle.orgadmin.adapters.outbound;

import com.processpuzzle.core.identity.IdentityProviderUnavailableException;
import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.PasswordPolicyViolationException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.outbound.SelfRegistrationPort.Registration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakSelfRegistrationAdapterTest {

    private static final String USERS = "/admin/realms/testbed/users";
    private static final Registration ADA = new Registration("ada@example.com", "s3cret!", "Ada", "Lovelace");

    private KeycloakAdminClient client;
    private KeycloakSelfRegistrationAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(KeycloakAdminClient.class);
        when(client.isConfigured()).thenReturn(true);
        adapter = new KeycloakSelfRegistrationAdapter(client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsAnEnabledAccountWithItsPasswordInOneCall() {
        when(client.createAndReturnId(eq(USERS), any())).thenReturn(Optional.of("u1"));

        adapter.register("testbed", ADA, false);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(client).createAndReturnId(eq(USERS), body.capture());
        assertThat(body.getValue())
                .containsEntry("username", "ada@example.com")
                .containsEntry("email", "ada@example.com")
                .containsEntry("enabled", true)
                .doesNotContainKey("requiredActions");
        assertThat((List<Map<String, Object>>) body.getValue().get("credentials")).singleElement()
                .satisfies(credential -> assertThat(credential)
                        .containsEntry("type", "password")
                        .containsEntry("value", "s3cret!")
                        .containsEntry("temporary", false));
        verify(client, never()).exchange(any(), anyString(), any(), any());
    }

    @Test
    void requestsVerificationWhenRequired() {
        when(client.createAndReturnId(eq(USERS), any())).thenReturn(Optional.of("u1"));

        adapter.register("testbed", ADA, true);

        verify(client).exchange(HttpMethod.PUT, USERS + "/u1/execute-actions-email", List.of("VERIFY_EMAIL"), null);
    }

    @Test
    void aFailedVerificationMailDoesNotFailTheRegistration() {
        when(client.createAndReturnId(eq(USERS), any())).thenReturn(Optional.of("u1"));
        when(client.exchange(any(), anyString(), any(), any()))
                .thenThrow(new IdentityProviderUnavailableException("smtp down"));

        adapter.register("testbed", ADA, true);
    }

    @Test
    void aDuplicateIsAConflict() {
        when(client.createAndReturnId(eq(USERS), any())).thenThrow(failure(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> adapter.register("testbed", ADA, false))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageNotContaining("testbed");
    }

    @Test
    void aRefusedPasswordIsABadRequest() {
        when(client.createAndReturnId(eq(USERS), any())).thenThrow(failure(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.register("testbed", ADA, false))
                .isInstanceOf(PasswordPolicyViolationException.class);
    }

    @Test
    void anyOtherFailureIsUnavailable() {
        when(client.createAndReturnId(eq(USERS), any())).thenThrow(failure(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> adapter.register("testbed", ADA, false))
                .isInstanceOf(DirectoryUnavailableException.class);
    }

    @Test
    void refusesWhenNoIdentityProviderIsConfigured() {
        when(client.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> adapter.register("testbed", ADA, false))
                .isInstanceOf(DirectoryUnavailableException.class);
        verify(client, never()).createAndReturnId(anyString(), any());
    }

    private static IdentityProviderUnavailableException failure(HttpStatus status) {
        return new IdentityProviderUnavailableException("failed", HttpClientErrorException.create(
                status, status.getReasonPhrase(), null, null, null));
    }
}
