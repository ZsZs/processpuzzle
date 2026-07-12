package com.processpuzzle.rule.usecase.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.processpuzzle.rule.usecase.ImportOutcome;
import com.processpuzzle.rule.usecase.ImportRules;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SampleRuleLoaderTest {

    private static Level originalLevel;

    private ImportRules importRules;
    private ResourcePatternResolver resourceResolver;
    private SampleRuleLoader loader;

    @BeforeAll
    static void silenceLoader() {
        // The "continuesWhenSingleFileFails" test intentionally makes execute() throw;
        // SampleRuleLoader.importSample() logs the exception at WARN, which otherwise
        // dumps a stack trace to the build console.
        Logger logger = (Logger) LoggerFactory.getLogger(SampleRuleLoader.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
    }

    @AfterAll
    static void restoreLoaderLogging() {
        ((Logger) LoggerFactory.getLogger(SampleRuleLoader.class)).setLevel(originalLevel);
    }

    @BeforeEach
    void setUp() {
        importRules = mock(ImportRules.class);
        resourceResolver = mock(ResourcePatternResolver.class);
        loader = new SampleRuleLoader(importRules, resourceResolver);
    }

    @Test
    void loadSamples_importsEachDiscoveredFile() throws IOException {
        Resource first = new NamedByteArrayResource("a.yaml", "rules: []".getBytes());
        Resource second = new NamedByteArrayResource("b.yaml", "rules: []".getBytes());
        when(resourceResolver.getResources("classpath:sample-rules/*.yaml"))
                .thenReturn(new Resource[]{first, second});
        when(importRules.execute(any(InputStream.class)))
                .thenReturn(new ImportOutcome(1, 0, List.of()));

        loader.loadSamples();

        verify(importRules, times(2)).execute(any(InputStream.class));
    }

    @Test
    void loadSamples_skipsWhenNoFilesFound() throws IOException {
        when(resourceResolver.getResources("classpath:sample-rules/*.yaml"))
                .thenReturn(new Resource[0]);

        loader.loadSamples();

        verify(importRules, never()).execute(any(InputStream.class));
    }

    @Test
    void loadSamples_continuesWhenSingleFileFails() throws IOException {
        Resource bad = new NamedByteArrayResource("bad.yaml", "rules: []".getBytes());
        Resource good = new NamedByteArrayResource("good.yaml", "rules: []".getBytes());
        when(resourceResolver.getResources("classpath:sample-rules/*.yaml"))
                .thenReturn(new Resource[]{bad, good});
        when(importRules.execute(any(InputStream.class)))
                .thenThrow(new IOException("boom"))
                .thenReturn(new ImportOutcome(1, 0, List.of()));

        loader.loadSamples();

        verify(importRules, times(2)).execute(any(InputStream.class));
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(String filename, byte[] bytes) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
