package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.usecase.exception.IdentityProviderUnavailableException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationAccessDeniedException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationAlreadyExistsException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationKeyInvalidException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationStatusConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each refusal has to arrive as a status the client can branch on and an {@code errorId} it can use
 * as a Transloco key — parsing prose is the alternative these ids exist to avoid.
 */
class PlatformAdminApiExceptionHandlerTest {

    private final PlatformAdminApiExceptionHandler handler = new PlatformAdminApiExceptionHandler();

    @Test
    void anUnknownTenantIs404() {
        var response = handler.handleNotFound(new OrganizationNotFoundException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("organization.not-found");
    }

    @Test
    void aTakenKeyIs409() {
        var response = handler.handleExists(new OrganizationAlreadyExistsException("my-org"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("organization.key.taken");
    }

    /** The id comes from the exception, so the form can tell "reserved" from "malformed". */
    @Test
    void aRejectedKeyIs400CarryingTheReasonAsItsErrorId() {
        var response = handler.handleKeyInvalid(
                new OrganizationKeyInvalidException("organization.key.reserved", "reserved"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("organization.key.reserved");
    }

    /** 403 rather than 404, so a non-staff caller cannot learn which orgKeys exist. */
    @Test
    void aNonStaffCallerIs403() {
        var response = handler.handleAccessDenied(new OrganizationAccessDeniedException("my-org"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("organization.access-denied");
    }

    @Test
    void anImpossibleTransitionIs409() {
        var response = handler.handleStatusConflict(new OrganizationStatusConflictException(
                "my-org", OrganizationStatus.PROVISIONING, "activate"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("organization.status.conflict");
    }

    /** 503, not 500: retryable, and not this platform's fault. */
    @Test
    void anUnreachableIdentityProviderIs503() {
        var response = handler.handleIdentityProvider(
                new IdentityProviderUnavailableException("keycloak is down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("identity-provider.unavailable");
    }

    /**
     * Both annotations, checked here as well as by the application-level {@code ApiAdviceScopeTest}.
     * This advice claims four exception types that {@code AppApiExceptionHandler} also claims, on the
     * same rung — safe only while the scopes are disjoint, so losing {@code basePackages} would make
     * which advice answers depend on bean ordering.
     */
    @Test
    void theAdviceIsScopedToThisModuleAndDeclaresItsRung() {
        RestControllerAdvice advice =
                PlatformAdminApiExceptionHandler.class.getAnnotation(RestControllerAdvice.class);

        assertThat(advice).isNotNull();
        assertThat(advice.basePackages()).containsExactly("com.processpuzzle.platformadmin");
        assertThat(PlatformAdminApiExceptionHandler.class.getAnnotation(Order.class))
                .isNotNull()
                .extracting(Order::value)
                .isEqualTo(com.processpuzzle.core.exception.ApiAdviceOrder.FEATURE);
    }
}
