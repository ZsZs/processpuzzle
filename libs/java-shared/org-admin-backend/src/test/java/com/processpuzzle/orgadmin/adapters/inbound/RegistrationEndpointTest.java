package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.orgadmin.model.UserRegistration;
import com.processpuzzle.orgadmin.usecases.inbound.RegisterUser;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RegistrationRateLimitedException;
import com.processpuzzle.orgadmin.usecases.outbound.SelfRegistrationPort.Registration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegistrationEndpointTest {

    private final RegisterUser registerUser = mock(RegisterUser.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final RegistrationEndpoint endpoint = new RegistrationEndpoint(registerUser, request);

    private static UserRegistration input() {
        UserRegistration input = new UserRegistration(" ada@example.com ", "pw");
        input.setFirstName("Ada");
        return input;
    }

    @Test
    void answers201AndKeysTheRateLimitOnTheProxiedAddress() {
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Real-IP", "203.0.113.9");

        var response = endpoint.registerUser(input());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(registerUser).execute("203.0.113.9", new Registration("ada@example.com", "pw", "Ada", null));
    }

    @Test
    void fallsBackToTheRemoteAddress() {
        request.setRemoteAddr("10.0.0.2");

        endpoint.registerUser(input());

        verify(registerUser).execute("10.0.0.2", new Registration("ada@example.com", "pw", "Ada", null));
    }

    @Test
    void rateLimitingAnswers429WithRetryAfter() {
        var response = new OrgAdminApiExceptionHandler()
                .handleRateLimited(new RegistrationRateLimitedException(Duration.ofSeconds(90)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("90");
    }
}
