package com.processpuzzle.rule.usecase.service;

import com.processpuzzle.rule.usecase.ImportOutcome;
import com.processpuzzle.rule.usecase.ImportRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SampleRuleLoaderTest {

    private ImportRules importRules;
    private ResourcePatternResolver resourceResolver;
    private SampleRuleLoader loader;

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
