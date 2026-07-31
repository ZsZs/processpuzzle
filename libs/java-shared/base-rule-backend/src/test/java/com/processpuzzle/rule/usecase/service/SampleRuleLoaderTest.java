package com.processpuzzle.rule.usecase.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.processpuzzle.rule.usecase.ImportOutcome;
import com.processpuzzle.rule.usecase.ImportRules;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SampleRuleLoaderTest {

    private static final String LOCATION = "classpath:sample-rules/*-rules.yaml";

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
        Resource first = new NamedByteArrayResource("a-rules.yaml", "rules: []".getBytes());
        Resource second = new NamedByteArrayResource("b-rules.yaml", "rules: []".getBytes());
        when(resourceResolver.getResources(LOCATION)).thenReturn(new Resource[]{first, second});
        when(importRules.execute(any(String.class), any(InputStream.class)))
                .thenReturn(new ImportOutcome(1, 0, List.of()));

        loader.loadSamples();

        verify(importRules).execute(eq("a"), any(InputStream.class));
        verify(importRules).execute(eq("b"), any(InputStream.class));
    }

    @Test
    void loadSamples_importsIntoTheOrganizationNamedByTheFile() throws IOException {
        // Rules are tenant-scoped, so the samples have to land in *some* organization; which one
        // is the part of the file name before '-rules.yaml'.
        when(resourceResolver.getResources(LOCATION))
                .thenReturn(new Resource[]{new NamedByteArrayResource("processpuzzle-testbed-rules.yaml", "rules: []".getBytes())});
        when(importRules.execute(any(String.class), any(InputStream.class)))
                .thenReturn(new ImportOutcome(1, 0, List.of()));

        loader.loadSamples();

        ArgumentCaptor<String> orgKey = ArgumentCaptor.forClass(String.class);
        verify(importRules).execute(orgKey.capture(), any(InputStream.class));
        assertThat(orgKey.getValue()).isEqualTo("processpuzzle-testbed");
    }

    @Test
    void loadSamples_skipsFileWithoutAnOrgKeyPrefix() throws IOException {
        when(resourceResolver.getResources(LOCATION))
                .thenReturn(new Resource[]{new NamedByteArrayResource("-rules.yaml", "rules: []".getBytes())});

        loader.loadSamples();

        verify(importRules, never()).execute(any(String.class), any(InputStream.class));
    }

    @Test
    void loadSamples_skipsWhenNoFilesFound() throws IOException {
        when(resourceResolver.getResources(LOCATION)).thenReturn(new Resource[0]);

        loader.loadSamples();

        verify(importRules, never()).execute(any(String.class), any(InputStream.class));
    }

    @Test
    void loadSamples_continuesWhenSingleFileFails() throws IOException {
        Resource bad = new NamedByteArrayResource("bad-rules.yaml", "rules: []".getBytes());
        Resource good = new NamedByteArrayResource("good-rules.yaml", "rules: []".getBytes());
        when(resourceResolver.getResources(LOCATION)).thenReturn(new Resource[]{bad, good});
        when(importRules.execute(any(String.class), any(InputStream.class)))
                .thenThrow(new IOException("boom"))
                .thenReturn(new ImportOutcome(1, 0, List.of()));

        loader.loadSamples();

        verify(importRules).execute(eq("bad"), any(InputStream.class));
        verify(importRules).execute(eq("good"), any(InputStream.class));
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
