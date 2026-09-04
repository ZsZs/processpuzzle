package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.usecase.exception.AppDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.exception.AppNotPublishedException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.exception.UnknownTenantException;
import com.processpuzzle.app.usecase.exception.RouteDefinitionNotFoundException;
import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Maps this feature's exceptions onto the {@code ErrorResponse} shape every 4xx in
 * base-app-api.yaml declares, so the frontend can key on {@code errorId} as a Transloco key rather
 * than parse prose.
 *
 * <p>Four of the types declared here are <em>not</em> this feature's own any more: the
 * {@code Organization*} exceptions moved to {@code com.processpuzzle.platformadmin.usecase.exception}
 * with the aggregate, and are declared here as well because
 * {@code @RestControllerAdvice(basePackages = ...)} matches on the package of the <b>controller</b>,
 * not of the exception. {@code AppEndpoint} still serves the five tenant-facing
 * {@code /organizations*} operations and still raises them, so without these handlers those five
 * endpoints would answer {@code 500 internal-error} instead of {@code organization.not-found} and
 * friends. {@code PlatformAdminApiExceptionHandler} declares the same four for its own controller;
 * two advices claiming one type on the same rung is safe precisely because their scopes are
 * disjoint — see {@link ApiAdviceOrder} and {@code ApiAdviceScopeTest}.
 *
 * <p>Beyond those, only this feature's own exception types are declared here.
 * {@code processpuzzle-core}'s {@code ApiExceptionHandler} already claims {@code IllegalArgumentException},
 * {@code IllegalStateException}, {@code JsonProcessingException},
 * {@code MethodArgumentNotValidException} and {@code InvalidDataAccessApiUsageException}; declaring
 * any of those a second time here would make which advice wins depend on bean ordering.
 *
 * <p>{@link ApiAdviceOrder#FEATURE} puts this advice ahead of core's, which is what keeps the ids
 * below from being answered by core's catch-all as {@code 500 internal-error} — see
 * {@link ApiAdviceOrder} for the incident that made the ladder explicit.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.app")
@Order(ApiAdviceOrder.FEATURE)
public class AppApiExceptionHandler {

    /**
     * Same {@code errorId} and status the relocated {@code OrganizationNotFoundException} produced.
     * Only the Java type changed: base-app raises its own now, rather than compiling against
     * platform-admin's, and a client cannot tell the difference.
     */
    @ExceptionHandler(UnknownTenantException.class)
    public ResponseEntity<ErrorResponse> handleUnknownTenant(UnknownTenantException ex) {
        return error(HttpStatus.NOT_FOUND, "organization.not-found", ex.getMessage());
    }

    @ExceptionHandler(AppDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAppNotFound(AppDefinitionNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "app.not-found", ex.getMessage());
    }

    @ExceptionHandler(RouteDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePageNotFound(RouteDefinitionNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "app.route.not-found", ex.getMessage());
    }

    @ExceptionHandler(ModuleDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleModuleNotFound(ModuleDefinitionNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "module.not-found", ex.getMessage());
    }

    @ExceptionHandler(ModuleDefinitionAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleModuleExists(ModuleDefinitionAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "module.already-exists", ex.getMessage());
    }

    /** Reports the first problem's identifier, for the reason given on {@link #handleInvalid}. */
    @ExceptionHandler(ModuleDefinitionInvalidException.class)
    public ResponseEntity<ErrorResponse> handleModuleInvalid(ModuleDefinitionInvalidException ex) {
        return error(HttpStatus.BAD_REQUEST, firstErrorId(ex.getProblems(), "module.validation.failed"),
                detailOf(ex.getProblems(), ex.getMessage()));
    }

    /** 404 rather than 409: the contract declares only 404 for the layout and route endpoints. */
    @ExceptionHandler(AppNotPublishedException.class)
    public ResponseEntity<ErrorResponse> handleNotPublished(AppNotPublishedException ex) {
        return error(HttpStatus.NOT_FOUND, "app.not-published", ex.getMessage());
    }

    @ExceptionHandler(AppDefinitionAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAppExists(AppDefinitionAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "app.already-exists", ex.getMessage());
    }

    /**
     * Reports the first validation problem's identifier so a client that only reads {@code errorId}
     * still gets something actionable; the full list is available from the validate endpoint.
     */
    @ExceptionHandler(AppDefinitionInvalidException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(AppDefinitionInvalidException ex) {
        return error(HttpStatus.BAD_REQUEST, firstErrorId(ex.getProblems(), "app.validation.failed"),
                detailOf(ex.getProblems(), ex.getMessage()));
    }

    private String firstErrorId(List<AppValidationProblem> problems, String fallback) {
        return problems.isEmpty() ? fallback : problems.getFirst().errorId();
    }

    private String detailOf(List<AppValidationProblem> problems, String fallback) {
        return problems.stream()
                .map(problem -> problem.path() + ": " + problem.errorText())
                .reduce((first, second) -> first + " | " + second)
                .orElse(fallback);
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(OrganizationAccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "organization.access-denied", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorId, errorText));
    }
}
