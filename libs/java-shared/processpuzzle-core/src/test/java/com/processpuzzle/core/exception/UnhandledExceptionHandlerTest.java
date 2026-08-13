package com.processpuzzle.core.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;

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
