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
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class LoggingAspectTest {

    private ListAppender<ILoggingEvent> plainAppender;
    private ListAppender<ILoggingEvent> annotatedAppender;
    private ListAppender<ILoggingEvent> nestedAppender;
    private Logger plainLogger;
    private Logger annotatedLogger;
    private Logger nestedLogger;

    @BeforeEach
    void setUp() {
        plainLogger = (Logger) LoggerFactory.getLogger(PlainService.class);
        annotatedLogger = (Logger) LoggerFactory.getLogger(AnnotatedService.class);
        nestedLogger = (Logger) LoggerFactory.getLogger(NestedService.class);
        plainLogger.setLevel(Level.TRACE);
        annotatedLogger.setLevel(Level.TRACE);
        nestedLogger.setLevel(Level.TRACE);
        plainLogger.setAdditive(false);
        annotatedLogger.setAdditive(false);
        nestedLogger.setAdditive(false);

        plainAppender = attach(plainLogger);
        annotatedAppender = attach(annotatedLogger);
        nestedAppender = attach(nestedLogger);
    }

    @AfterEach
    void tearDown() {
        plainLogger.detachAppender(plainAppender);
        annotatedLogger.detachAppender(annotatedAppender);
        nestedLogger.detachAppender(nestedAppender);
        plainLogger.setAdditive(true);
        annotatedLogger.setAdditive(true);
        nestedLogger.setAdditive(true);
    }

    @Test
    void logMethod_emitsEntryAndExitWithArgsAndResult() {
        PlainService proxy = proxy(new PlainService());

        String result = proxy.greet("world");

        assertThat(result).isEqualTo("hello world");
        assertThat(plainAppender.list).hasSize(2);

        ILoggingEvent entry = plainAppender.list.get(0);
        assertThat(entry.getLevel()).isEqualTo(Level.INFO);
        assertThat(entry.getFormattedMessage()).isEqualTo("→ PlainService.greet({\"name\":\"world\"})");
        assertThat(entry.getMDCPropertyMap())
                .containsEntry(LoggingAspect.MDC_CLASS, "PlainService")
                .containsEntry(LoggingAspect.MDC_METHOD, "greet")
                .containsEntry(LoggingAspect.MDC_ARGS, "{\"name\":\"world\"}")
                .containsEntry(LoggingAspect.MDC_DEPTH, "0")
                .containsKey(LoggingAspect.MDC_CALL_ID)
                .doesNotContainKey(LoggingAspect.MDC_PARENT_CALL_ID);

        ILoggingEvent exit = plainAppender.list.get(1);
        assertThat(exit.getFormattedMessage()).isEqualTo("← PlainService.greet = \"hello world\"");
        assertThat(exit.getMDCPropertyMap())
                .containsEntry(LoggingAspect.MDC_RESULT, "\"hello world\"")
                .containsEntry(LoggingAspect.MDC_CALL_ID, entry.getMDCPropertyMap().get(LoggingAspect.MDC_CALL_ID));
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
        assertThat(errorEvent.getFormattedMessage()).isEqualTo("✗ PlainService.explode threw IllegalStateException");
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
        assertThat(exit.getFormattedMessage()).isEqualTo("← AnnotatedService.publicOne = void");
    }

    @Test
    void nestedCalls_indentAndLinkParentChild() {
        NestedService target = new NestedService();
        NestedService proxy = proxy(target);
        target.bindSelf(proxy);

        proxy.outer();

        assertThat(nestedAppender.list).hasSize(4);

        ILoggingEvent outerEntry = nestedAppender.list.get(0);
        ILoggingEvent innerEntry = nestedAppender.list.get(1);
        ILoggingEvent innerExit = nestedAppender.list.get(2);
        ILoggingEvent outerExit = nestedAppender.list.get(3);

        assertThat(outerEntry.getFormattedMessage()).startsWith("→ NestedService.outer");
        assertThat(innerEntry.getFormattedMessage()).startsWith("  → NestedService.inner");
        assertThat(innerExit.getFormattedMessage()).startsWith("  ← NestedService.inner");
        assertThat(outerExit.getFormattedMessage()).startsWith("← NestedService.outer");

        assertThat(outerEntry.getMDCPropertyMap())
                .containsEntry(LoggingAspect.MDC_DEPTH, "0")
                .doesNotContainKey(LoggingAspect.MDC_PARENT_CALL_ID);
        assertThat(innerEntry.getMDCPropertyMap())
                .containsEntry(LoggingAspect.MDC_DEPTH, "1")
                .containsEntry(LoggingAspect.MDC_PARENT_CALL_ID, outerEntry.getMDCPropertyMap().get(LoggingAspect.MDC_CALL_ID));
    }

    @Test
    void multipartFileArg_isSummarizedNotSerialized() {
        OpaqueArgService proxy = proxy(new OpaqueArgService());
        Logger logger = (Logger) LoggerFactory.getLogger(OpaqueArgService.class);
        logger.setLevel(Level.TRACE);
        logger.setAdditive(false);
        ListAppender<ILoggingEvent> appender = attach(logger);

        try {
            MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3, 4});

            proxy.upload(file);

            assertThat(appender.list).hasSize(2);
            String argsJson = appender.list.get(0).getMDCPropertyMap().get(LoggingAspect.MDC_ARGS);
            assertThat(argsJson).contains("\"originalFilename\":\"photo.png\"");
            assertThat(argsJson).contains("\"size\":4");
            assertThat(argsJson).contains("\"contentType\":\"image/png\"");
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(true);
        }
    }

    @Test
    void byteArrayArg_isRenderedAsCompactSummary() {
        OpaqueArgService proxy = proxy(new OpaqueArgService());
        Logger logger = (Logger) LoggerFactory.getLogger(OpaqueArgService.class);
        logger.setLevel(Level.TRACE);
        logger.setAdditive(false);
        ListAppender<ILoggingEvent> appender = attach(logger);

        try {
            proxy.storeBytes(new byte[]{1, 2, 3, 4, 5});

            String argsJson = appender.list.get(0).getMDCPropertyMap().get(LoggingAspect.MDC_ARGS);
            assertThat(argsJson).isEqualTo("{\"bytes\":\"<byte[5]>\"}");
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(true);
        }
    }

    @Test
    void inputStreamArg_isRenderedAsPlaceholder() {
        OpaqueArgService proxy = proxy(new OpaqueArgService());
        Logger logger = (Logger) LoggerFactory.getLogger(OpaqueArgService.class);
        logger.setLevel(Level.TRACE);
        logger.setAdditive(false);
        ListAppender<ILoggingEvent> appender = attach(logger);

        try {
            proxy.consume(new ByteArrayInputStream(new byte[]{1, 2}));

            String argsJson = appender.list.get(0).getMDCPropertyMap().get(LoggingAspect.MDC_ARGS);
            assertThat(argsJson).isEqualTo("{\"stream\":\"<InputStream>\"}");
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(true);
        }
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

    @LogClass
    static class OpaqueArgService {
        public void upload(org.springframework.web.multipart.MultipartFile file) {
            // no-op
        }

        public void storeBytes(byte[] bytes) {
            // no-op
        }

        public void consume(InputStream stream) {
            // no-op
        }
    }

    @LogClass
    static class NestedService {
        private NestedService self;

        public void bindSelf(NestedService self) {
            this.self = self;
        }

        public int outer() {
            return self != null ? self.inner(2) : inner(2);
        }

        public int inner(int value) {
            return value * 10;
        }
    }
}
