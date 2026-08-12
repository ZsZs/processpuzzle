package com.processpuzzle.core.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The most-used advice in the codebase and, until the error body was standardized, the least tested.
 * Every assertion below is about the wire: the status, and the {@code errorId}/{@code errorText} pair
 * that {@code shared-api.yaml}'s {@code ErrorResponse} declares.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureLogging() {
        logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLogging() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void anIllegalArgumentIs400() {
        var response = handler.handleBadRequest(new IllegalArgumentException("orgKey must not be blank"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorId()).isEqualTo("request.invalid-argument");
        assertThat(response.getBody().errorText()).isEqualTo("orgKey must not be blank");
    }

    /**
     * An RSQL coercion failure reaches us wrapped by Spring's exception translator. It reports the same
     * id as a direct {@code IllegalArgumentException}: to the caller it is the same mistake, and the
     * wrapping is a detail of our persistence layer.
     */
    @Test
    void aWrappedIllegalArgumentFromTheRsqlSpecificationIsAlso400WithTheRootMessage() {
        var wrapped = new InvalidDataAccessApiUsageException("translated", new IllegalArgumentException("not a number: 'abc'"));

        var response = handler.handleDaoApiUsage(wrapped);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorId()).isEqualTo("request.invalid-argument");
        assertThat(response.getBody().errorText()).isEqualTo("not a number: 'abc'");
    }

    @Test
    void aDaoMisuseThatIsNotAnIllegalArgumentIsRethrownRatherThanReportedAs400() {
        var wrapped = new InvalidDataAccessApiUsageException("query is broken", new IllegalStateException("no session"));

        assertThatThrownBy(() -> handler.handleDaoApiUsage(wrapped)).isSameAs(wrapped);
    }

    @Test
    void anIllegalStateIs409() {
        var response = handler.handleConflictState(new IllegalStateException("already published"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().errorId()).isEqualTo("request.illegal-state");
        assertThat(response.getBody().errorText()).isEqualTo("already published");
    }

    @Test
    void anUnparseablePayloadIs400AndSaysSo() {
        var response = handler.handleParseError(new JsonParseException(null, "unexpected character"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorId()).isEqualTo("request.malformed-payload");
        assertThat(response.getBody().errorText()).startsWith("Could not parse YAML: ");
    }

    /**
     * The field errors are flattened into {@code errorText}. They used to be the body's keys, which made
     * the shape depend on the payload — the one thing a declared schema cannot describe.
     */
    @Test
    void validationFailuresAreFlattenedIntoErrorTextRatherThanBecomingTheBodysKeys() throws Exception {
        var response = handler.handleValidation(validationFailure());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorId()).isEqualTo("request.validation-failed");
        assertThat(response.getBody().errorText()).isEqualTo("slug: must not be blank; title: must not be blank");
    }

    @Test
    void anUnexpectedExceptionIs500WithAGenericTextSoNothingInternalLeaks() {
        var response = handler.handleUnexpected(new RuntimeException("jdbc:postgresql://secret-host:5432/prod failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().errorId()).isEqualTo("internal-error");
        assertThat(response.getBody().errorText()).isEqualTo("Unexpected server error.");
        assertThat(response.getBody().errorText()).doesNotContain("secret-host");
    }

    @Test
    void anUnexpectedExceptionIsStillLoggedInFullSoOperatorsLoseNothing() {
        handler.handleUnexpected(new RuntimeException("jdbc:postgresql://secret-host:5432/prod failed"));

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
        assertThat(appender.list.getFirst().getThrowableProxy().getMessage()).contains("secret-host");
    }

    private static MethodArgumentNotValidException validationFailure() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "documentInput");
        bindingResult.addError(new FieldError("documentInput", "slug", "must not be blank"));
        bindingResult.addError(new FieldError("documentInput", "title", "must not be blank"));

        var method = ApiExceptionHandlerTest.class.getDeclaredMethod("validationFailure");
        return new MethodArgumentNotValidException(new org.springframework.core.MethodParameter(method, -1), bindingResult);
    }
}
