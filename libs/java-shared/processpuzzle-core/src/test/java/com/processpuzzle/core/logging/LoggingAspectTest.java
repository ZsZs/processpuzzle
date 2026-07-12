package com.processpuzzle.core.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class LoggingAspectTest {

    private ListAppender<ILoggingEvent> plainAppender;
    private ListAppender<ILoggingEvent> annotatedAppender;
    private Logger plainLogger;
    private Logger annotatedLogger;

    @BeforeEach
    void setUp() {
        plainLogger = (Logger) LoggerFactory.getLogger(PlainService.class);
        annotatedLogger = (Logger) LoggerFactory.getLogger(AnnotatedService.class);
        plainLogger.setLevel(Level.TRACE);
        annotatedLogger.setLevel(Level.TRACE);
        plainLogger.setAdditive(false);
        annotatedLogger.setAdditive(false);

        plainAppender = attach(plainLogger);
        annotatedAppender = attach(annotatedLogger);
    }

    @AfterEach
    void tearDown() {
        plainLogger.detachAppender(plainAppender);
        annotatedLogger.detachAppender(annotatedAppender);
        plainLogger.setAdditive(true);
        annotatedLogger.setAdditive(true);
    }

    @Test
    void logMethod_emitsEntryAndExitWithArgsAndResult() {
        PlainService proxy = proxy(new PlainService());

        String result = proxy.greet("world");

        assertThat(result).isEqualTo("hello world");
        assertThat(plainAppender.list).hasSize(2);

        ILoggingEvent entry = plainAppender.list.get(0);
        assertThat(entry.getLevel()).isEqualTo(Level.INFO);
        assertThat(entry.getMessage()).isEqualTo("method entry");
        assertThat(entry.getMDCPropertyMap())
                .containsEntry(LoggingAspect.MDC_CLASS, "PlainService")
                .containsEntry(LoggingAspect.MDC_METHOD, "greet")
                .containsEntry(LoggingAspect.MDC_ARGS, "{\"name\":\"world\"}");

        ILoggingEvent exit = plainAppender.list.get(1);
        assertThat(exit.getMessage()).isEqualTo("method exit");
        assertThat(exit.getMDCPropertyMap()).containsEntry(LoggingAspect.MDC_RESULT, "\"hello world\"");
    }

    @Test
    void logMethod_honoursConfiguredLevel() {
        PlainService proxy = proxy(new PlainService());

        proxy.compute(3);

        assertThat(plainAppender.list).allSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.DEBUG));
    }

    @Test
    void logClass_coversPublicMethods() {
        AnnotatedService proxy = proxy(new AnnotatedService());

        proxy.publicOne();
        proxy.publicTwo();

        assertThat(annotatedAppender.list).hasSize(4);
        assertThat(annotatedAppender.list).allSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.INFO));
    }

    @Test
    void logMethodOnClass_takesPrecedenceOverLogClass() {
        AnnotatedService proxy = proxy(new AnnotatedService());

        proxy.overridden();

        assertThat(annotatedAppender.list).hasSize(2);
        assertThat(annotatedAppender.list).allSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void exception_isLoggedAtErrorAndRethrown() {
        PlainService proxy = proxy(new PlainService());

        Throwable thrown = catchThrowable(proxy::explode);

        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("boom");
        ILoggingEvent errorEvent = plainAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .findFirst()
                .orElseThrow();
        assertThat(errorEvent.getMessage()).isEqualTo("method threw");
        assertThat(errorEvent.getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    @Test
    void disabledLevel_emitsNoEntryOrExit() {
        plainLogger.setLevel(Level.WARN);
        PlainService proxy = proxy(new PlainService());

        proxy.greet("silent");

        assertThat(plainAppender.list).isEmpty();
    }

    @Test
    void voidMethod_reportsVoidAsResult() {
        AnnotatedService proxy = proxy(new AnnotatedService());

        proxy.publicOne();

        ILoggingEvent exit = annotatedAppender.list.get(1);
        assertThat(exit.getMDCPropertyMap()).containsEntry(LoggingAspect.MDC_RESULT, "void");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new LoggingAspect());
        return (T) factory.getProxy();
    }

    private static ListAppender<ILoggingEvent> attach(Logger logger) {
        // Sync ListAppender never triggers Logback's lazy MDC snapshot; force it here
        // so events retain their MDC (production Logstash encoder does the same via
        // event.getMDCPropertyMap() during formatting).
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    static class PlainService {
        @LogMethod
        public String greet(String name) {
            return "hello " + name;
        }

        @LogMethod(level = org.slf4j.event.Level.DEBUG)
        public int compute(int value) {
            return value * 2;
        }

        @LogMethod
        public void explode() {
            throw new IllegalStateException("boom");
        }
    }

    @LogClass
    static class AnnotatedService {
        public void publicOne() {
            // no-op
        }

        public Map<String, String> publicTwo() {
            return Map.of("k", "v");
        }

        @LogMethod(level = org.slf4j.event.Level.WARN)
        public void overridden() {
            // no-op
        }
    }
}
