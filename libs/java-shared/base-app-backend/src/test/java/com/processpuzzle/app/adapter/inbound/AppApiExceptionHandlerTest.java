package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.exception.AppDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.AppNotPublishedException;
import com.processpuzzle.app.usecase.exception.UnknownTenantException;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.exception.RouteDefinitionNotFoundException;
import com.processpuzzle.shared.model.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.processpuzzle.app.AppTestFixtures.APP_ID;
import static com.processpuzzle.app.AppTestFixtures.ORG_KEY;
import static com.processpuzzle.app.AppTestFixtures.ROUTE_PATH;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every 4xx in base-app-api.yaml declares the {@code ErrorResponse} shape, and the frontend keys on
 * {@code errorId} as a Transloco key rather than parsing prose — so the identifier a handler emits is
 * part of the contract, not a log message.
 */
class AppApiExceptionHandlerTest {

    private final AppApiExceptionHandler handler = new AppApiExceptionHandler();

    /**
     * Unchanged errorId and status. Only the exception type changed: base-app raises its own
     * {@link UnknownTenantException} rather than platform-admin's {@code OrganizationNotFoundException},
     * and this is what pins that a client cannot tell.
     */
    @Test
    void anUnknownOrganizationIs404() {
        assertThatResponse(handler.handleUnknownTenant(new UnknownTenantException(ORG_KEY)))
                .isEqualTo(HttpStatus.NOT_FOUND, "organization.not-found", ORG_KEY);
    }

    @Test
    void anUnknownAppDefinitionIs404() {
        assertThatResponse(handler.handleAppNotFound(new AppDefinitionNotFoundException(ORG_KEY, APP_ID)))
                .isEqualTo(HttpStatus.NOT_FOUND, "app.not-found", APP_ID);
    }

    @Test
    void anUnknownPageIs404() {
        assertThatResponse(handler.handlePageNotFound(
                new RouteDefinitionNotFoundException(ORG_KEY, APP_ID, ROUTE_PATH)))
                .isEqualTo(HttpStatus.NOT_FOUND, "app.route.not-found", ROUTE_PATH);
    }

    /** 404 rather than 409: the contract declares only 404 for the layout and route endpoints. */
    @Test
    void requestingAnUnpublishedRevisionIs404NotAConflict() {
        assertThatResponse(handler.handleNotPublished(new AppNotPublishedException(ORG_KEY, APP_ID)))
                .isEqualTo(HttpStatus.NOT_FOUND, "app.not-published", APP_ID);
    }

    @Test
    void aTakenAppDefinitionIdIs409() {
        assertThatResponse(handler.handleAppExists(new AppDefinitionAlreadyExistsException(ORG_KEY, APP_ID)))
                .isEqualTo(HttpStatus.CONFLICT, "app.already-exists", APP_ID);
    }

    @Test
    void aDeniedTenantIs403() {
        assertThatResponse(handler.handleAccessDenied(new OrganizationAccessDeniedException(ORG_KEY)))
                .isEqualTo(HttpStatus.FORBIDDEN, "organization.access-denied", ORG_KEY);
    }

    /**
     * A client that only reads {@code errorId} still gets something actionable, so the first problem's
     * identifier wins; the detail concatenates all of them.
     */
    @Test
    void aRejectedWriteReportsTheFirstProblemsIdentifierAndEveryProblemsText() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalid(
                new AppDefinitionInvalidException(ORG_KEY, APP_ID, List.of(
                        new AppValidationProblem("/id", "app.validation.missing-id", "Needs an id."),
                        new AppValidationProblem("/name", "app.validation.missing-name", "Needs a name."))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("app.validation.missing-id");
        assertThat(response.getBody().getErrorText())
                .isEqualTo("/id: Needs an id. | /name: Needs a name.");
    }

    @Test
    void aRejectedWriteWithASingleProblemDoesNotConcatenate() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalid(
                new AppDefinitionInvalidException(ORG_KEY, APP_ID, List.of(
                        new AppValidationProblem("/id", "app.validation.missing-id", "Needs an id."))));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorText()).isEqualTo("/id: Needs an id.");
    }

    /** Thrown from a path that carried no problems: the response still has to be renderable. */
    @Test
    void aRejectedWriteWithoutProblemsFallsBackToAGenericIdentifierAndTheExceptionMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalid(
                new AppDefinitionInvalidException(ORG_KEY, APP_ID, List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("app.validation.failed");
        assertThat(response.getBody().getErrorText()).contains(ORG_KEY + "/" + APP_ID);
    }

    private static ErrorResponseAssert assertThatResponse(ResponseEntity<ErrorResponse> response) {
        return new ErrorResponseAssert(response);
    }

    /** Keeps each expectation above to the three things that matter: status, errorId, and detail. */
    private record ErrorResponseAssert(ResponseEntity<ErrorResponse> response) {

        void isEqualTo(HttpStatus status, String errorId, String detailFragment) {
            assertThat(response.getStatusCode()).isEqualTo(status);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getErrorId()).isEqualTo(errorId);
            assertThat(response.getBody().getErrorText()).contains(detailFragment);
        }
    }
}
