package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.platformadmin.usecase.exception.IdentityProviderUnavailableException;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationAlreadyExistsException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationKeyInvalidException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationStatusConflictException;
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this feature's exceptions onto the {@code ErrorResponse} shape every 4xx in
 * platform-admin-api.yaml declares, so the frontend can key on {@code errorId} as a Transloco key
 * rather than parse prose.
 *
 * <p>{@code basePackages} is not optional and neither is the {@code @Order} — see
 * {@link ApiAdviceOrder} for the incident that made the ladder explicit, and
 * {@code ApiAdviceScopeTest} for the check that enforces both halves. The scope matters more here
 * than usual: {@code AppApiExceptionHandler} declares four of the same exception types, because
 * base-app's endpoints raise them too and advice scoping matches on the controller's package rather
 * than the exception's. Two advices sharing a rung and a type is safe only while their scopes stay
 * disjoint, which is exactly what these two annotations guarantee.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.platformadmin")
@Order(ApiAdviceOrder.FEATURE)
public class PlatformAdminApiExceptionHandler {

    @ExceptionHandler(OrganizationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(OrganizationNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "organization.not-found", ex.getMessage());
    }

    @ExceptionHandler(OrganizationAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleExists(OrganizationAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "organization.key.taken", ex.getMessage());
    }

    @ExceptionHandler(OrganizationKeyInvalidException.class)
    public ResponseEntity<ErrorResponse> handleKeyInvalid(OrganizationKeyInvalidException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getErrorId(), ex.getMessage());
    }

    /**
     * 403 rather than 404 across the whole {@code /platform/**} surface. Answering 404 would tell a
     * non-staff caller whether an {@code orgKey} exists; 403 says only that the caller is not staff.
     */
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(OrganizationAccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "organization.access-denied", ex.getMessage());
    }

    @ExceptionHandler(OrganizationStatusConflictException.class)
    public ResponseEntity<ErrorResponse> handleStatusConflict(OrganizationStatusConflictException ex) {
        return error(HttpStatus.CONFLICT, "organization.status.conflict", ex.getMessage());
    }

    /**
     * 503, not 500: the identity provider being unreachable is retryable and not this platform's
     * fault, and the distinction is actionable — a 503 from {@code assignOrganizationAdmin} means the
     * user was not created and the same request can simply be sent again.
     */
    @ExceptionHandler(IdentityProviderUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleIdentityProvider(IdentityProviderUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "identity-provider.unavailable", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorId, errorText));
    }
}
