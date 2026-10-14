package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.InvitationRecoveryRequiredException;
import com.processpuzzle.orgadmin.model.InvitationRecoveryRequired;
import com.processpuzzle.orgadmin.usecases.inbound.exception.OrganizationSuspendedException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.PasswordPolicyViolationException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RegistrationDisabledException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.RegistrationRateLimitedException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownOrganizationException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UnknownRoleException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserAlreadyExistsException;
import com.processpuzzle.orgadmin.usecases.inbound.exception.UserNotFoundException;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this feature's refusals onto the {@code ErrorResponse} shape every 4xx in org-admin-api.yaml
 * declares.
 *
 * <p>Every type here is now either this module's own or {@code core}'s. The unknown-tenant one used
 * to be {@code platform-admin}'s {@code OrganizationNotFoundException} and is now
 * {@link UnknownOrganizationException}; {@code OrganizationAccessDeniedException} moved to
 * {@code core.tenancy} earlier. The error ids did not change — they belong to org-admin-api.yaml,
 * not to the classes.
 *
 * <p>{@code OrganizationAccessDeniedException} is still declared here rather than left to a
 * platform-wide advice, because {@code @RestControllerAdvice(basePackages = ...)} matches on the
 * <b>controller's</b> package and not the exception's: without it a bad {@code orgKey} on an
 * org-admin endpoint would come back as {@code 500 internal-error}. Several advices on the
 * {@code FEATURE} rung therefore claim that one type, which is safe only because their scopes are
 * disjoint — see {@link ApiAdviceOrder} and {@code ApiAdviceScopeTest}.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.orgadmin")
@Order(ApiAdviceOrder.FEATURE)
public class OrgAdminApiExceptionHandler {

    @ExceptionHandler(UnknownOrganizationException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationNotFound(UnknownOrganizationException ex) {
        return error(HttpStatus.NOT_FOUND, "organization.not-found", ex.getMessage());
    }

    /**
     * 404, not 403. A suspended tenant's administrator has no standing to be told "forbidden" — from
     * their side the organization has stopped existing, and 403 would invite a retry that cannot
     * succeed until platform staff lift the suspension.
     */
    @ExceptionHandler(OrganizationSuspendedException.class)
    public ResponseEntity<ErrorResponse> handleSuspended(OrganizationSuspendedException ex) {
        return error(HttpStatus.NOT_FOUND, "organization.suspended", ex.getMessage());
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(OrganizationAccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "organization.access-denied", ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "user.not-found", ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserExists(UserAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "user.already-exists", ex.getMessage());
    }

    /** 400: the role is refused rather than created, so the client has to correct the name. */
    @ExceptionHandler(UnknownRoleException.class)
    public ResponseEntity<ErrorResponse> handleUnknownRole(UnknownRoleException ex) {
        return error(HttpStatus.BAD_REQUEST, "role.unknown", ex.getMessage());
    }

    @ExceptionHandler(InvitationRecoveryRequiredException.class)
    public ResponseEntity<InvitationRecoveryRequired> handleInvitationRecovery(InvitationRecoveryRequiredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new InvitationRecoveryRequired(
                "user.invitation-recovery-required", ex.getMessage(), ex.getOperationId())
                .userId(ex.getUserId()));
    }

    /** Provider failure; retry safety is operation-specific, not implied by HTTP 503. */
    @ExceptionHandler(DirectoryUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDirectoryUnavailable(DirectoryUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "user-directory.unavailable", ex.getMessage());
    }

    @ExceptionHandler(RegistrationDisabledException.class)
    public ResponseEntity<ErrorResponse> handleRegistrationDisabled(RegistrationDisabledException ex) {
        return error(HttpStatus.NOT_FOUND, "registration.disabled", ex.getMessage());
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<ErrorResponse> handlePasswordPolicy(PasswordPolicyViolationException ex) {
        return error(HttpStatus.BAD_REQUEST, "registration.password-policy", ex.getMessage());
    }

    @ExceptionHandler(RegistrationRateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(RegistrationRateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.getRetryAfter().toSeconds())))
                .body(new ErrorResponse("registration.rate-limited", ex.getMessage()));
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorId, errorText));
    }
}
