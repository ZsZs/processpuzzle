package com.processpuzzle.core.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 500 body, and the ordering that keeps it from being everyone's answer.
 */
class UnhandledExceptionHandlerTest {

    private final UnhandledExceptionHandler handler = new UnhandledExceptionHandler();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureLogging() {
        logger = (Logger) LoggerFactory.getLogger(UnhandledExceptionHandler.class);
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

    /**
     * The case that motivated {@code ErrorResponse} being consulted at all: a browser asking for
     * {@code /favicon.svg}, several times a page load, each one previously a {@code 500} plus a full
     * stack trace at {@code ERROR}.
     */
    @Test
    void aMissingStaticResourceIs404NotAServerFault() {
        var response = handler.handleUnexpected(
                new NoResourceFoundException(HttpMethod.GET, "favicon.svg", "/favicon.svg"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().errorId()).isEqualTo("request.not-found");
        assertThat(response.getBody().errorText()).contains("favicon.svg");
    }

    @Test
    void aMissingStaticResourceDoesNotFillTheLogWithStackTraces() {
        handler.handleUnexpected(new NoResourceFoundException(
                HttpMethod.GET, "assets/fonts/Inter-Regular.woff2", "/assets/fonts/Inter-Regular.woff2"));

        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    /**
     * The whole {@link org.springframework.web.ErrorResponse} family, not a favicon special case: every
     * standard Spring MVC exception declares its own status, and none of them is a 500.
     */
    @Test
    void anyExceptionDeclaringItsOwnStatusAnswersWithIt() {
        var notAllowed = handler.handleUnexpected(
                new HttpRequestMethodNotSupportedException("TRACE", List.of("GET", "POST")));
        var unsupportedType = handler.handleUnexpected(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertThat(notAllowed.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(notAllowed.getBody().errorId()).isEqualTo("request.method-not-allowed");
        assertThat(unsupportedType.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(unsupportedType.getBody().errorId()).isEqualTo("request.unsupported-media-type");
    }

    /** A 5xx that carries its own status is still a fault, so it keeps its stack trace. */
    @Test
    void aSelfDeclared5xxIsStillLoggedAsAnError() {
        var response = handler.handleUnexpected(new AsyncRequestTimeoutException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(appender.list).anyMatch(event -> event.getLevel() == Level.ERROR);
    }

    /**
     * Spring answers with the first <em>advice</em> that has any matching handler, so this one losing
     * every tie is the only thing that keeps a feature's {@code document.not-found} from being reported
     * as {@code internal-error}. It was unordered for a day, and that is precisely what happened.
     */
    @Test
    void thisAdviceRunsAfterCoresAndThereforeAfterEveryFeatureAdvice() {
        assertThat(orderOf(UnhandledExceptionHandler.class)).isEqualTo(ApiAdviceOrder.CATCH_ALL);
        assertThat(orderOf(ApiExceptionHandler.class)).isEqualTo(ApiAdviceOrder.GENERIC);
        assertThat(ApiAdviceOrder.FEATURE).isLessThan(ApiAdviceOrder.GENERIC);
        assertThat(ApiAdviceOrder.GENERIC).isLessThan(ApiAdviceOrder.CATCH_ALL);
    }

    private static int orderOf(Class<?> advice) {
        Order order = advice.getAnnotation(Order.class);
        assertThat(order).as("%s must declare its precedence explicitly", advice.getSimpleName()).isNotNull();
        return order.value();
    }
}
