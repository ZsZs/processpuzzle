package com.processpuzzle.core.logging;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class HandledExceptionClassifierTest {

    @Test
    void discoversAdviceLazilyAfterContextRefresh() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var classifier = new HandledExceptionClassifier(context);
            context.register(ConflictAdvice.class);
            context.refresh();

            assertThat(classifier.isHandled(getClass(), new ConflictException())).isTrue();
            assertThat(classifier.isHandled(getClass(), new IllegalStateException())).isFalse();
        }
    }

    @Test
    void concurrentFirstCallsClassifyHandledAndUnhandledExceptions() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(ConflictAdvice.class);
             var executor = Executors.newFixedThreadPool(8)) {
            var classifier = new HandledExceptionClassifier(context);
            Callable<Boolean> classify = () ->
                    classifier.isHandled(getClass(), new ConflictException())
                            && !classifier.isHandled(getClass(), new IllegalStateException());

            for (Future<Boolean> result : executor.invokeAll(Collections.nCopies(32, classify))) {
                assertThat(result.get()).isTrue();
            }
        }
    }

    static class ConflictException extends RuntimeException {
    }

    @RestControllerAdvice
    static class ConflictAdvice {
        @ExceptionHandler(ConflictException.class)
        String handle(ConflictException exception) {
            return exception.getMessage();
        }
    }
}
