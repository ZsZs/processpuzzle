package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.identity.KeycloakAdminClient;
import com.processpuzzle.core.security.SecurityProperties;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RegistrationDisabledException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RegistrationRateLimitedException;
import com.processpuzzle.orgadmin.usecases.outbound.SelfRegistrationPort;
import com.processpuzzle.orgadmin.usecases.outbound.SelfRegistrationPort.Registration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterUserTest {

    private static final Registration ADA = new Registration("ada@example.com", "pw", null, null);

    private final RegistrationProperties properties = new RegistrationProperties();
    private final SecurityProperties security = new SecurityProperties();
    private KeycloakAdminClient client;
    private RegistrationRateLimiter limiter;
    private SelfRegistrationPort port;
    private RegisterUser useCase;

    @BeforeEach
    void setUp() {
        client = mock(KeycloakAdminClient.class);
        when(client.organizationRealm()).thenReturn(Optional.empty());
        limiter = mock(RegistrationRateLimiter.class);
        port = mock(SelfRegistrationPort.class);
        security.setStackRealm("testbed");
        useCase = new RegisterUser(properties, security, client, limiter, port);
    }

    @Test
    void registersIntoTheStackRealm() {
        properties.setEnabled(true);
        properties.setVerifyEmail(true);

        useCase.execute("1.1.1.1", ADA);

        verify(limiter).acquire("1.1.1.1");
        verify(port).register("testbed", ADA, true);
    }

    @Test
    void isClosedByDefault() {
        assertThatThrownBy(() -> useCase.execute("1.1.1.1", ADA)).isInstanceOf(RegistrationDisabledException.class);
        verify(limiter, never()).acquire(anyString());
        verify(port, never()).register(anyString(), any(), anyBoolean());
    }

    @Test
    void isClosedInOrganizationMode() {
        properties.setEnabled(true);
        when(client.organizationRealm()).thenReturn(Optional.of("shared"));

        assertThatThrownBy(() -> useCase.execute("1.1.1.1", ADA)).isInstanceOf(RegistrationDisabledException.class);
    }

    @Test
    void createsNothingWhenRateLimited() {
        properties.setEnabled(true);
        doThrow(new RegistrationRateLimitedException(java.time.Duration.ofMinutes(1))).when(limiter).acquire(anyString());

        assertThatThrownBy(() -> useCase.execute("1.1.1.1", ADA)).isInstanceOf(RegistrationRateLimitedException.class);
        verify(port, never()).register(anyString(), any(), anyBoolean());
    }
}
